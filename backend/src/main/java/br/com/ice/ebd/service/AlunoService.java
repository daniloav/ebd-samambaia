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

    @Inject EscopoService escopo;

    @Inject
    AlunoRepository repository;

    @Inject
    ClasseService classeService;

    public List<AlunoResponse> listar(Long classeId, boolean apenasAtivos) {
        escopo.assertClasse(classeId);
        List<Aluno> alunos;
        if (classeId != null) {
            alunos = apenasAtivos ? repository.listarAtivosPorClasse(classeId)
                                  : repository.listarPorClasse(classeId);
        } else {
            alunos = apenasAtivos ? repository.listarAtivos() : repository.listarOrdenadoPorNome();
        }
        return alunos.stream().map(AlunoResponse::de).toList();
    }

    public AlunoResponse buscar(Long id) {
        Aluno a = obter(id);
        escopo.assertClasse(a.getClasse().getId());
        return AlunoResponse.de(a);
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
        escopo.assertClasse(a.getClasse().getId());
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
        escopo.assertClasse(req.classeId());
        a.setNome(req.nome().trim());
        a.setTelefone(req.telefone());
        a.setDataNascimento(req.dataNascimento());
        a.setAtivo(req.ativo() == null ? true : req.ativo());
        a.setClasse(classeService.obter(req.classeId()));
        a.setEmail(req.email() != null && !req.email().isBlank() ? req.email().trim() : null);
        a.setRecebeNotificacoes(req.recebeNotificacoes() != null && req.recebeNotificacoes());
    }
}
