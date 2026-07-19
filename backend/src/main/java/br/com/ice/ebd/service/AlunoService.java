package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.AlunoRequest;
import br.com.ice.ebd.dto.AlunoResponse;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.repository.AlunoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import java.util.List;

@ApplicationScoped
public class AlunoService {

    @Inject
    AlunoRepository repository;

    public List<AlunoResponse> listar(boolean apenasAtivos) {
        List<Aluno> alunos = apenasAtivos ? repository.listarAtivos() : repository.listarOrdenadoPorNome();
        return alunos.stream().map(AlunoResponse::de).toList();
    }

    public AlunoResponse buscar(Long id) {
        return AlunoResponse.de(obter(id));
    }

    @Transactional
    public AlunoResponse criar(AlunoRequest req) {
        Aluno a = new Aluno();
        aplicar(a, req);
        repository.persist(a);
        return AlunoResponse.de(a);
    }

    @Transactional
    public AlunoResponse atualizar(Long id, AlunoRequest req) {
        Aluno a = obter(id);
        aplicar(a, req);
        return AlunoResponse.de(a);
    }

    @Transactional
    public void deletar(Long id) {
        Aluno a = obter(id);
        repository.delete(a);
    }

    private Aluno obter(Long id) {
        Aluno a = repository.findById(id);
        if (a == null) {
            throw new NotFoundException("Aluno não encontrado: " + id);
        }
        return a;
    }

    private void aplicar(Aluno a, AlunoRequest req) {
        a.setNome(req.nome().trim());
        a.setTelefone(req.telefone());
        a.setDataNascimento(req.dataNascimento());
        a.setAtivo(req.ativo() == null ? true : req.ativo());
    }
}
