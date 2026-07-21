package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.CampanhaImagem;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class CampanhaImagemRepository implements PanacheRepository<CampanhaImagem> {

    public List<CampanhaImagem> listarPorCampanha(Long campanhaId) {
        return list("campanha.id = ?1 order by ordem, id", campanhaId);
    }

    /** Metadados (id, nome, tipo) sem carregar os bytes — para listagens. */
    public List<Object[]> metadataPorCampanha(Long campanhaId) {
        return getEntityManager().createQuery(
                "select i.id, i.nome, i.tipo from CampanhaImagem i "
                + "where i.campanha.id = ?1 order by i.ordem, i.id", Object[].class)
                .setParameter(1, campanhaId).getResultList();
    }
}
