package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Presenca;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * IDs dos alunos presentes na aula de uma turma numa data (presente = true). Usado para
     * lançar nota de prova offline apenas para quem esteve na aula daquela data.
     */
    public Set<Long> idsPresentesNaClasseEData(Long classeId, LocalDate data) {
        return getEntityManager().createQuery(
                        "select p.aluno.id from Presenca p where p.presente = true "
                                + "and p.aula.classe.id = :cid and p.aula.data = :data", Long.class)
                .setParameter("cid", classeId).setParameter("data", data)
                .getResultStream().collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public void deletarPorAula(Long aulaId) {
        delete("aula.id", aulaId);
    }
}
