package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.AulaRequest;
import br.com.ice.ebd.dto.AulaResponse;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.AcaoAuditoria;
import br.com.ice.ebd.model.EntidadeAuditoria;
import br.com.ice.ebd.repository.AulaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
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

    public List<AulaResponse> listar(Long classeId) {
        escopo.assertClasse(classeId);
        var aulas = classeId != null ? repository.listarPorClasse(classeId) : repository.listarOrdenadoPorData();
        return aulas.stream().map(AulaResponse::de).toList();
    }

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
        auditoria.registrar(AcaoAuditoria.ATUALIZAR, EntidadeAuditoria.AULA, a.getId(), rotulo(a));
        return AulaResponse.de(a);
    }

    @Transactional
    public void deletar(Long id) {
        Aula a = obter(id);
        auditoria.registrar(AcaoAuditoria.EXCLUIR, EntidadeAuditoria.AULA, a.getId(), rotulo(a));
        repository.delete(a); // presenças são removidas em cascata (FK ON DELETE CASCADE)
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
