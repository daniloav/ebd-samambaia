package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Aula;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class AulaRepository implements PanacheRepository<Aula> {

    public List<Aula> listarOrdenadoPorData() {
        return listAll(Sort.by("data").descending());
    }

    public List<Aula> listarPorClasse(Long classeId) {
        return list("classe.id = ?1 order by data desc", classeId);
    }

    public Optional<Aula> findByClasseAndData(Long classeId, LocalDate data) {
        return find("classe.id = ?1 and data = ?2", classeId, data).firstResultOptional();
    }

    /**
     * IDs de alunos vinculados a professores que dão <b>alguma</b> aula na data informada
     * (nesta ou em qualquer outra turma). Esses alunos estão dando aula nesse dia e, por isso,
     * não recebem presença como alunos na chamada daquele dia. Num dia em que não dão aula,
     * contam normalmente como alunos.
     */
    public Set<Long> alunoIdsDeProfessoresComAulaEm(LocalDate data) {
        return getEntityManager().createQuery(
                        "select distinct prof.aluno.id from Aula a join a.professor prof "
                                + "where a.data = :data and prof.aluno.id is not null", Long.class)
                .setParameter("data", data)
                .getResultStream().collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
