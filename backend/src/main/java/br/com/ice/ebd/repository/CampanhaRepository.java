package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Campanha;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class CampanhaRepository implements PanacheRepository<Campanha> {

    /** Histórico de campanhas, da mais recente para a mais antiga. */
    public List<Campanha> listarRecentes() {
        return list("order by dataEnvio desc");
    }
}
