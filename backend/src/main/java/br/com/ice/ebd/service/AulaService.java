package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.AulaComplementarRequest;
import br.com.ice.ebd.dto.AulaComplementarResponse;
import br.com.ice.ebd.dto.AulaRequest;
import br.com.ice.ebd.dto.AulaResponse;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.repository.UsuarioRepository;
import br.com.ice.ebd.model.AcaoAuditoria;
import br.com.ice.ebd.model.EntidadeAuditoria;
import br.com.ice.ebd.repository.AulaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class AulaService {

    @Inject EscopoService escopo;
    @Inject AuditoriaService auditoria;

    @Inject
    AulaRepository repository;

    @Inject
    ClasseService classeService;

    @Inject
    UsuarioRepository usuarioRepository;

    @Transactional
    public List<AulaResponse> listar(Long classeId) {
        escopo.assertClasse(classeId);
        var aulas = classeId != null ? repository.listarPorClasse(classeId) : repository.listarOrdenadoPorData();
        return aulas.stream().map(AulaResponse::de).toList();
    }

    @Transactional
    public AulaResponse buscar(Long id) {
        Aula a = obter(id);
        escopo.assertClasse(a.getClasse().getId());
        return AulaResponse.de(a);
    }

    @Transactional
    public AulaResponse criar(AulaRequest req) {
        escopo.assertClasse(req.classeId());
        var classe = classeService.obter(req.classeId());
        validarDataUnica(classe.getId(), req.data(), null);
        Aula a = new Aula();
        a.setClasse(classe);
        a.setData(req.data());
        a.setTema(req.tema());
        a.setProfessor(resolverProfessor(req.professorId()));
        repository.persist(a);
        auditoria.registrar(AcaoAuditoria.CRIAR, EntidadeAuditoria.AULA, a.getId(), rotulo(a));
        return AulaResponse.de(a);
    }

    @Transactional
    public AulaResponse atualizar(Long id, AulaRequest req) {
        escopo.assertClasse(req.classeId());
        Aula a = obter(id);
        var classe = classeService.obter(req.classeId());
        validarDataUnica(classe.getId(), req.data(), id);
        a.setClasse(classe);
        a.setData(req.data());
        a.setTema(req.tema());
        a.setProfessor(resolverProfessor(req.professorId()));
        auditoria.registrar(AcaoAuditoria.ATUALIZAR, EntidadeAuditoria.AULA, a.getId(), rotulo(a));
        return AulaResponse.de(a);
    }

    @Transactional
    public void deletar(Long id) {
        Aula a = obter(id);
        auditoria.registrar(AcaoAuditoria.EXCLUIR, EntidadeAuditoria.AULA, a.getId(), rotulo(a));
        repository.delete(a); // presenças são removidas em cascata (FK ON DELETE CASCADE)
    }

    /**
     * Desdobra uma aula: cria a <b>continuação no próximo domingo</b> (origem + 7 dias) e
     * <b>empurra +7 dias toda a agenda seguinte</b> da mesma turma, preservando tema e professor
     * de cada aula. O empurrão é feito da aula mais recente para a mais antiga, com flush por
     * iteração, para nunca colidir com a unique {@code uq_aula_classe_data} (não-deferrable):
     * a mais recente vai para o slot vazio e cada aula anterior ocupa o slot recém-liberado.
     */
    @Transactional
    public AulaComplementarResponse complementar(Long origemId, AulaComplementarRequest req) {
        Aula origem = obter(origemId);
        Long classeId = origem.getClasse().getId();
        escopo.assertClasse(classeId);

        LocalDate novaData = origem.getData().plusDays(7);

        // Empurra a agenda seguinte (já vem em ordem decrescente de data).
        var seguintes = repository.listarPorClasseDesde(classeId, novaData);
        for (Aula a : seguintes) {
            a.setData(a.getData().plusDays(7));
            repository.getEntityManager().flush();
        }

        // Cria a aula complementar no domingo recém-liberado.
        Aula nova = new Aula();
        nova.setClasse(origem.getClasse());
        nova.setData(novaData);
        nova.setTema(temaComplemento(req.tema(), origem.getTema()));
        nova.setProfessor(req.professorId() != null ? resolverProfessor(req.professorId()) : origem.getProfessor());
        validarDataUnica(classeId, novaData, null); // sanidade: já deve estar livre após o empurrão
        repository.persist(nova);

        auditoria.registrar(AcaoAuditoria.CRIAR, EntidadeAuditoria.AULA, nova.getId(),
                "complementar de " + origem.getData() + " · " + rotulo(nova));
        if (!seguintes.isEmpty()) {
            auditoria.registrar(AcaoAuditoria.ATUALIZAR, EntidadeAuditoria.AULA, origem.getId(),
                    "empurrão +7d na agenda da turma: " + seguintes.size() + " aula(s)");
        }
        return new AulaComplementarResponse(AulaResponse.de(nova), seguintes.size());
    }

    /** Tema informado, ou o da origem com sufixo "(continuação)". */
    private static String temaComplemento(String temaInformado, String temaOrigem) {
        if (temaInformado != null && !temaInformado.isBlank()) {
            return temaInformado;
        }
        if (temaOrigem == null || temaOrigem.isBlank()) {
            return null;
        }
        return temaOrigem + " (continuação)";
    }

    /** Resolve o professor (usuário PROFESSOR) do id, ou null. Valida o perfil. */
    private Usuario resolverProfessor(Long professorId) {
        if (professorId == null) {
            return null;
        }
        Usuario u = usuarioRepository.findById(professorId);
        if (u == null || !u.isEhProfessor()) {
            throw new WebApplicationException("Professor inválido para a aula.", Response.Status.BAD_REQUEST);
        }
        return u;
    }

    public Aula obter(Long id) {
        Aula a = repository.findById(id);
        if (a == null) {
            throw new NotFoundException("Aula não encontrada: " + id);
        }
        return a;
    }

    private void validarDataUnica(Long classeId, java.time.LocalDate data, Long idAtual) {
        Optional<Aula> existente = repository.findByClasseAndData(classeId, data);
        if (existente.isPresent() && !existente.get().getId().equals(idAtual)) {
            throw new WebApplicationException("Já existe uma aula desta classe nesta data.",
                    Response.Status.CONFLICT);
        }
    }

    private static String rotulo(br.com.ice.ebd.model.Aula a) {
        String tema = a.getTema() == null ? "" : a.getTema();
        return a.getData() + (tema.isBlank() ? "" : " · " + tema);
    }
}
