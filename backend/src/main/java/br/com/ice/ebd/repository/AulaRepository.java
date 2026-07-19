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

    public Optional<Aula> findByData(LocalDate data) {
        return find("data", data).firstResultOptional();
    }
}
