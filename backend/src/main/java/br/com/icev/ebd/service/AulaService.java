package br.com.icev.ebd.service;

import br.com.icev.ebd.dto.AulaRequest;
import br.com.icev.ebd.dto.AulaResponse;
import br.com.icev.ebd.model.Aula;
import br.com.icev.ebd.repository.AulaRepository;
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

    @Inject
    AulaRepository repository;

    public List<AulaResponse> listar() {
        return repository.listarOrdenadoPorData().stream().map(AulaResponse::de).toList();
    }

    public AulaResponse buscar(Long id) {
        return AulaResponse.de(obter(id));
    }

    @Transactional
    public AulaResponse criar(AulaRequest req) {
        validarDataUnica(req.data(), null);
        Aula a = new Aula();
        a.setData(req.data());
        a.setTema(req.tema());
        repository.persist(a);
        return AulaResponse.de(a);
    }

    @Transactional
    public AulaResponse atualizar(Long id, AulaRequest req) {
        Aula a = obter(id);
        validarDataUnica(req.data(), id);
        a.setData(req.data());
        a.setTema(req.tema());
        return AulaResponse.de(a);
    }

    @Transactional
    public void deletar(Long id) {
        Aula a = obter(id);
        repository.delete(a); // presenças são removidas em cascata (FK ON DELETE CASCADE)
    }

    public Aula obter(Long id) {
        Aula a = repository.findById(id);
        if (a == null) {
            throw new NotFoundException("Aula não encontrada: " + id);
        }
        return a;
    }

    private void validarDataUnica(java.time.LocalDate data, Long idAtual) {
        Optional<Aula> existente = repository.findByData(data);
        if (existente.isPresent() && !existente.get().getId().equals(idAtual)) {
            throw new WebApplicationException("Já existe uma aula cadastrada nesta data.",
                    Response.Status.CONFLICT);
        }
    }
}
