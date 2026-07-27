package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.ClasseRequest;
import br.com.ice.ebd.dto.ClasseResponse;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.repository.ClasseRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ClasseService {

    @Inject ClasseRepository repository;
    @Inject EntityManager em;
    @Inject EscopoService escopo;
    @Inject br.com.ice.ebd.repository.UsuarioRepository usuarioRepository;

    /** Lista as classes que o usuário pode ver: ADMIN todas; PROFESSOR só as vinculadas. */
    public List<ClasseResponse> listar(boolean apenasAtivas) {
        List<Classe> classes = apenasAtivas ? repository.listarAtivas() : repository.listarOrdenado();
        Set<Long> permitidas = escopo.classesPermitidas(); // null = todas (ADMIN)
        if (permitidas != null) {
            classes = classes.stream().filter(c -> permitidas.contains(c.getId())).toList();
        }
        return classes.stream().map(ClasseResponse::de).toList();
    }

    public ClasseResponse buscar(Long id) {
        escopo.assertClasse(id);
        return ClasseResponse.de(obter(id));
    }

    public Classe obter(Long id) {
        Classe c = repository.findById(id);
        if (c == null) {
            throw new NotFoundException("Classe não encontrada: " + id);
        }
        return c;
    }

    @Transactional
    public ClasseResponse criar(ClasseRequest req) {
        Classe c = new Classe();
        aplicar(c, req);
        repository.persist(c);
        return ClasseResponse.de(c);
    }

    @Transactional
    public ClasseResponse atualizar(Long id, ClasseRequest req) {
        Classe c = obter(id);
        aplicar(c, req);
        return ClasseResponse.de(c);
    }

    @Transactional
    public void deletar(Long id) {
        Classe c = obter(id);
        long alunos = em.createQuery("select count(a) from Aluno a where a.classe.id = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        long aulas = em.createQuery("select count(a) from Aula a where a.classe.id = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        long provas = em.createQuery("select count(p) from Prova p where p.classe.id = :id", Long.class)
                .setParameter("id", id).getSingleResult();
        if (alunos > 0 || aulas > 0 || provas > 0) {
            throw new WebApplicationException(
                    "Não é possível excluir: a classe tem alunos, aulas ou provas vinculados. "
                            + "Inative-a em vez de excluir.", Response.Status.CONFLICT);
        }
        repository.delete(c);
    }

    private void aplicar(Classe c, ClasseRequest req) {
        c.setNome(req.nome().trim());
        c.setDescricao(req.descricao());
        c.setAtivo(req.ativo() == null ? true : req.ativo());
    }

    /** Professores da classe (para o seletor "Professor da aula"). Respeita o escopo do usuário. */
    public java.util.List<br.com.ice.ebd.dto.ProfessorResumo> listarProfessores(Long classeId) {
        escopo.assertClasse(classeId);
        return usuarioRepository.professoresDaClasse(classeId).stream()
                .map(br.com.ice.ebd.dto.ProfessorResumo::de).toList();
    }
}
