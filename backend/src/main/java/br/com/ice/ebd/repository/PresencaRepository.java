package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Presenca;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class PresencaRepository implements PanacheRepository<Presenca> {

    public List<Presenca> listarPorAula(Long aulaId) {
        return list("aula.id", aulaId);
    }

    public List<Presenca> listarPorAluno(Long alunoId) {
        return list("aluno.id", alunoId);
    }

    /** Presença de um aluno numa aula específica (ou null). */
    public Presenca buscarPorAulaEAluno(Long aulaId, Long alunoId) {
        return find("aula.id = ?1 and aluno.id = ?2", aulaId, alunoId).firstResult();
    }

    /** Presenças de um aluno numa turma, da mais recente para a mais antiga (por data da aula). */
    public List<Presenca> listarPorAlunoDesc(Long alunoId) {
        return list("aluno.id = ?1 order by aula.data desc", alunoId);
    }

    public void deletarPorAula(Long aulaId) {
        delete("aula.id", aulaId);
    }
}
