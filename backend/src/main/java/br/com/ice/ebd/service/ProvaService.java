package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.NotaItem;
import br.com.ice.ebd.dto.NotasProvaResponse;
import br.com.ice.ebd.dto.ProvaRequest;
import br.com.ice.ebd.dto.ProvaResponse;
import br.com.ice.ebd.dto.SalvarNotasRequest;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.NotaProva;
import br.com.ice.ebd.model.Prova;
import br.com.ice.ebd.model.AcaoAuditoria;
import br.com.ice.ebd.model.EntidadeAuditoria;
import br.com.ice.ebd.model.TipoProva;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.NotaProvaRepository;
import br.com.ice.ebd.repository.PresencaRepository;
import br.com.ice.ebd.repository.ProvaRepository;
import br.com.ice.ebd.repository.QuestaoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ProvaService {

    @Inject EscopoService escopo;

    @Inject AuditoriaService auditoria;

    @Inject ProvaRepository provaRepository;
    @Inject ClasseService classeService;
    @Inject NotaProvaRepository notaRepository;
    @Inject QuestaoRepository questaoRepository;
    @Inject AlunoRepository alunoRepository;
    @Inject PresencaRepository presencaRepository;
    @Inject NotificacaoService notificacaoService;

    // ---------- CRUD da prova ----------

    public List<ProvaResponse> listar(Long classeId) {
        escopo.assertClasse(classeId);
        var provas = classeId != null ? provaRepository.listarPorClasse(classeId)
                                       : provaRepository.listarOrdenadoPorData();
        return provas.stream().map(p -> ProvaResponse.de(p, questaoRepository.count("prova.id", p.getId()))).toList();
    }

    public ProvaResponse buscar(Long id) {
        Prova p = obter(id);
        escopo.assertClasse(p.getClasse().getId());
        return ProvaResponse.de(p, questaoRepository.count("prova.id", p.getId()));
    }

    @Transactional
    public ProvaResponse criar(ProvaRequest req) {
        escopo.assertClasse(req.classeId());
        Prova p = new Prova();
        aplicar(p, req);
        provaRepository.persist(p);
        auditoria.registrar(AcaoAuditoria.CRIAR, EntidadeAuditoria.PROVA, p.getId(), p.getTitulo());
        return ProvaResponse.de(p);
    }

    @Transactional
    public ProvaResponse atualizar(Long id, ProvaRequest req) {
        escopo.assertClasse(req.classeId());
        Prova p = obter(id);
        aplicar(p, req);
        auditoria.registrar(AcaoAuditoria.ATUALIZAR, EntidadeAuditoria.PROVA, p.getId(), p.getTitulo());
        return ProvaResponse.de(p);
    }

    @Transactional
    public void deletar(Long id) {
        Prova p = obter(id);
        auditoria.registrar(AcaoAuditoria.EXCLUIR, EntidadeAuditoria.PROVA, p.getId(), p.getTitulo());
        provaRepository.delete(p); // notas removidas em cascata
    }

    // ---------- Notas ----------

    /**
     * Grade de notas: os alunos elegíveis, com a nota lançada (ou null). Numa prova OFFLINE só
     * entram os alunos <b>presentes</b> na aula da data da prova; nas demais, todos os ativos.
     */
    public NotasProvaResponse obterNotas(Long provaId) {
        Prova prova = obter(provaId);
        escopo.assertClasse(prova.getClasse().getId());
        Map<Long, BigDecimal> notasPorAluno = new LinkedHashMap<>();
        for (NotaProva n : notaRepository.listarPorProva(provaId)) {
            notasPorAluno.put(n.getAluno().getId(), n.getNota());
        }
        List<NotaItem> itens = alunosElegiveis(prova).stream()
                .map(a -> new NotaItem(a.getId(), a.getNome(), notasPorAluno.get(a.getId())))
                .toList();
        return new NotasProvaResponse(prova.getId(), prova.getTitulo(), prova.getData(),
                prova.getNotaMaxima(), prova.getTipo() == TipoProva.OFFLINE, itens);
    }

    @Transactional
    public NotasProvaResponse salvarNotas(Long provaId, SalvarNotasRequest req) {
        Prova prova = obter(provaId);
        escopo.assertClasse(prova.getClasse().getId());

        // Prova OFFLINE: só é permitido lançar nota para quem esteve presente na aula da data.
        Set<Long> presentes = prova.getTipo() == TipoProva.OFFLINE
                ? presencaRepository.idsPresentesNaClasseEData(prova.getClasse().getId(), prova.getData())
                : null; // null = sem restrição de presença

        Map<Long, NotaProva> existentes = new LinkedHashMap<>();
        for (NotaProva n : notaRepository.listarPorProva(provaId)) {
            existentes.put(n.getAluno().getId(), n);
        }

        for (SalvarNotasRequest.Item item : req.itens()) {
            if (item.nota() == null) {
                // nota em branco: remove registro existente, se houver
                NotaProva existente = existentes.get(item.alunoId());
                if (existente != null) {
                    notaRepository.delete(existente);
                }
                continue;
            }
            if (presentes != null && !presentes.contains(item.alunoId())) {
                throw new WebApplicationException(
                        "Só é possível lançar nota para alunos presentes na aula da data desta prova.",
                        Response.Status.BAD_REQUEST);
            }
            if (item.nota().compareTo(prova.getNotaMaxima()) > 0) {
                throw new WebApplicationException(
                        "A nota do aluno " + item.alunoId() + " excede a nota máxima da prova ("
                                + prova.getNotaMaxima() + ").",
                        Response.Status.BAD_REQUEST);
            }
            Aluno aluno = alunoRepository.findById(item.alunoId());
            if (aluno == null) {
                throw new NotFoundException("Aluno não encontrado: " + item.alunoId());
            }
            NotaProva n = existentes.get(item.alunoId());
            if (n == null) {
                n = new NotaProva();
                n.setProva(prova);
                n.setAluno(aluno);
            }
            n.setNota(item.nota());
            notaRepository.persist(n);
        }
        return obterNotas(provaId);
    }

    /**
     * "Lançar e notificar": envia a cada aluno com nota lançada o e-mail do seu desempenho.
     * Respeita o opt-in ({@code recebeNotificacoes}), como os alertas de chamada. Retorna
     * quantos e-mails foram enviados.
     */
    @Transactional
    public int notificarNotas(Long provaId) {
        Prova prova = obter(provaId);
        escopo.assertClasse(prova.getClasse().getId());
        int enviados = 0;
        for (NotaProva n : notaRepository.listarPorProva(provaId)) {
            Aluno a = n.getAluno();
            if (a.getEmail() == null || a.getEmail().isBlank() || !a.isRecebeNotificacoes()) {
                continue;
            }
            if (n.getNotificadaNota() != null && n.getNotificadaNota().compareTo(n.getNota()) == 0) {
                continue; // mesma nota já notificada — não reenvia
            }
            if (notificacaoService.enviarNotaProva(a, prova, n.getNota())) {
                n.setNotificadaNota(n.getNota()); // marca a nota notificada
                enviados++;
            }
        }
        return enviados;
    }

    // ---------- helpers ----------

    /**
     * Alunos que podem receber nota nesta prova. Prova OFFLINE: só os presentes na aula da data
     * da prova (mesma turma). Demais tipos: todos os ativos da turma.
     */
    private List<Aluno> alunosElegiveis(Prova prova) {
        List<Aluno> ativos = alunoRepository.listarAtivosPorClasse(prova.getClasse().getId());
        if (prova.getTipo() != TipoProva.OFFLINE) {
            return ativos;
        }
        Set<Long> presentes = presencaRepository.idsPresentesNaClasseEData(
                prova.getClasse().getId(), prova.getData());
        return ativos.stream().filter(a -> presentes.contains(a.getId())).toList();
    }

    private Prova obter(Long id) {
        Prova p = provaRepository.findById(id);
        if (p == null) {
            throw new NotFoundException("Prova não encontrada: " + id);
        }
        return p;
    }

    private void aplicar(Prova p, ProvaRequest req) {
        p.setClasse(classeService.obter(req.classeId()));
        p.setTitulo(req.titulo().trim());
        p.setData(req.data());
        p.setNotaMaxima(req.notaMaxima());
        p.setTipo(req.tipo() != null && req.tipo().equalsIgnoreCase("ONLINE") ? TipoProva.ONLINE : TipoProva.OFFLINE);
        p.setAbreEm(req.abreEm());
        p.setFechaEm(req.fechaEm());
    }
}
