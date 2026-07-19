package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Aula;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
}
