package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Alternativa;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class AlternativaRepository implements PanacheRepository<Alternativa> {
    public List<Alternativa> listarPorQuestao(Long questaoId) {
        return list("questao.id = ?1 order by ordem, id", questaoId);
    }
}
