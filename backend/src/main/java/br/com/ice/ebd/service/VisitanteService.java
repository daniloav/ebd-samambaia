package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.VisitanteRequest;
import br.com.ice.ebd.dto.VisitanteResponse;
import br.com.ice.ebd.model.AcaoAuditoria;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.EntidadeAuditoria;
import br.com.ice.ebd.model.Visitante;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.AulaRepository;
import br.com.ice.ebd.repository.UsuarioRepository;
import br.com.ice.ebd.repository.VisitanteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.text.Normalizer;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Visitantes de uma aula. Ao registrar, envia boas-vindas ao visitante e avisa
 * os professores. Respeita o escopo por turma (professor só na turma dele).
 *
 * <p>Regra de negócio: um visitante que comparece a {@value #AULAS_PARA_PROMOVER} aulas
 * seguidas da turma vira <b>aluno</b> automaticamente (identidade = nome + telefone/e-mail).</p>
 */
@ApplicationScoped
public class VisitanteService {

    private static final Logger LOG = Logger.getLogger(VisitanteService.class);

    /** Aulas seguidas frequentadas por um visitante para promovê-lo a aluno. */
    private static final int AULAS_PARA_PROMOVER = 3;

    @Inject VisitanteRepository repository;
    @Inject AlunoRepository alunoRepository;
    @Inject AulaRepository aulaRepository;
    @Inject UsuarioRepository usuarioRepository;
    @Inject AulaService aulaService;
    @Inject EscopoService escopo;
    @Inject NotificacaoService notificacaoService;
    @Inject AcessoAlunoService acessoAluno;
    @Inject AuditoriaService auditoria;

    public List<VisitanteResponse> listar(Long aulaId) {
        Aula aula = aulaService.obter(aulaId);
        escopo.assertClasse(aula.getClasse().getId());
        return repository.listarPorAula(aulaId).stream().map(VisitanteResponse::de).toList();
    }

    @Transactional
    public VisitanteResponse adicionar(Long aulaId, VisitanteRequest req) {
        Aula aula = aulaService.obter(aulaId);
        escopo.assertClasse(aula.getClasse().getId());

        Visitante v = new Visitante();
        v.setAula(aula);
        v.setNome(req.nome().trim());
        v.setEmail(vazioNull(req.email()));
        v.setTelefone(vazioNull(req.telefone()));
        if (req.trazidoPorAlunoId() != null) {
            Aluno a = alunoRepository.findById(req.trazidoPorAlunoId());
            if (a == null) {
                throw new NotFoundException("Aluno não encontrado: " + req.trazidoPorAlunoId());
            }
            v.setTrazidoPor(a);
        }
        repository.persist(v);

        // E-mails (opt-in do servidor via toggle; falhas viram log, não quebram o cadastro).
        notificacaoService.enviarBoasVindasVisitante(v);
        notificacaoService.avisarProfessoresNovoVisitante(v, usuarioRepository.emailsDeProfessoresAtivos());

        String alerta = promoverSeFrequente(v, aula);
        return VisitanteResponse.de(v).comAlerta(alerta);
    }

    @Transactional
    public void remover(Long id) {
        Visitante v = repository.findById(id);
        if (v == null) {
            throw new NotFoundException("Visitante não encontrado: " + id);
        }
        escopo.assertClasse(v.getAula().getClasse().getId());
        repository.delete(v);
    }

    /**
     * Se este visitante já compareceu às {@value #AULAS_PARA_PROMOVER} aulas mais recentes da turma
     * (incluindo esta), promove-o a aluno. Identidade por nome + (telefone OU e-mail). Idempotente:
     * não promove se já houver aluno equivalente na turma, nem se faltar contato para confirmar.
     *
     * @return mensagem de aviso quando promove; {@code null} caso contrário.
     */
    private String promoverSeFrequente(Visitante v, Aula aula) {
        // Sem contato não dá para confirmar identidade entre aulas — não promove.
        if (fone(v.getTelefone()).isBlank() && email(v.getEmail()).isBlank()) {
            return null;
        }
        // 3 aulas mais recentes da turma com data <= a desta.
        List<Aula> recentes = aulaRepository.listarPorClasse(aula.getClasse().getId()).stream()
                .filter(a -> !a.getData().isAfter(aula.getData()))
                .limit(AULAS_PARA_PROMOVER)
                .toList();
        if (recentes.size() < AULAS_PARA_PROMOVER) {
            return null;
        }
        for (Aula a : recentes) {
            boolean presente = repository.listarPorAula(a.getId()).stream().anyMatch(o -> mesmoVisitante(v, o));
            if (!presente) {
                return null; // faltou em alguma das aulas da janela
            }
        }
        if (jaExisteAluno(aula.getClasse().getId(), v)) {
            return null; // já promovido / já é aluno
        }

        Aluno novo = new Aluno();
        novo.setNome(v.getNome());
        novo.setTelefone(v.getTelefone());
        novo.setEmail(v.getEmail());
        novo.setClasse(aula.getClasse());
        novo.setAtivo(true);
        alunoRepository.persist(novo);
        alunoRepository.flush();               // garante o id antes de vincular o login
        acessoAluno.sincronizarAcesso(novo);   // login automático (senha padrão + troca no 1º acesso)

        String msg = String.format("%s virou aluno(a): compareceu a %d aulas seguidas como visitante.",
                novo.getNome(), AULAS_PARA_PROMOVER);
        auditoria.registrar(AcaoAuditoria.CRIAR, EntidadeAuditoria.ALUNO, novo.getId(), msg);
        // Boas-vindas como aluno (best-effort; respeita toggle/e-mail).
        notificacaoService.avisarVisitantePromovido(novo, usuarioRepository.loginDoAluno(novo.getId()));
        LOG.info(msg);
        return msg;
    }

    /** Mesmo visitante: nome normalizado igual E (telefone OU e-mail) coincidindo. */
    private boolean mesmoVisitante(Visitante a, Visitante b) {
        if (!nome(a.getNome()).equals(nome(b.getNome())) || nome(a.getNome()).isBlank()) {
            return false;
        }
        return contatoBate(fone(a.getTelefone()), fone(b.getTelefone()))
                || contatoBate(email(a.getEmail()), email(b.getEmail()));
    }

    private boolean jaExisteAluno(Long classeId, Visitante v) {
        return alunoRepository.listarPorClasse(classeId).stream().anyMatch(al ->
                nome(al.getNome()).equals(nome(v.getNome())) && !nome(v.getNome()).isBlank()
                && (contatoBate(fone(al.getTelefone()), fone(v.getTelefone()))
                    || contatoBate(email(al.getEmail()), email(v.getEmail()))));
    }

    private static boolean contatoBate(String a, String b) {
        return !a.isBlank() && a.equals(b);
    }

    private static String nome(String s) {
        String limpo = Normalizer.normalize(s == null ? "" : s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().replaceAll("\\s+", " ").trim();
        return limpo;
    }

    private static String fone(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    private static String email(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private String vazioNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }
}
