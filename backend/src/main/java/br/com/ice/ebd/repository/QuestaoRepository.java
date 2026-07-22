package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Questao;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class QuestaoRepository implements PanacheRepository<Questao> {
    public List<Questao> listarPorProva(Long provaId) {
        return list("prova.id = ?1 order by ordem, id", provaId);
    }
    public void apagarPorProva(Long provaId) {
        delete("prova.id = ?1", provaId); // alternativas caem por cascade no banco
    }
}
