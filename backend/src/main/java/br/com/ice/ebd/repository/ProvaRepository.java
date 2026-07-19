package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Prova;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class ProvaRepository implements PanacheRepository<Prova> {

    public List<Prova> listarOrdenadoPorData() {
        return listAll(Sort.by("data").descending());
    }
}
