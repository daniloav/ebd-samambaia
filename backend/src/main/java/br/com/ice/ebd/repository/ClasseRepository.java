package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Classe;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class ClasseRepository implements PanacheRepository<Classe> {

    public List<Classe> listarOrdenado() {
        return list("order by nome");
    }

    public List<Classe> listarAtivas() {
        return list("ativo = true order by nome");
    }
}
