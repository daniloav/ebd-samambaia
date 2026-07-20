package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Visitante;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class VisitanteRepository implements PanacheRepository<Visitante> {

    public List<Visitante> listarPorAula(Long aulaId) {
        return list("aula.id = ?1 order by dataCadastro", aulaId);
    }

    /** Total de visitantes registrados nas aulas de uma data (todas as turmas). */
    public long contarPorData(LocalDate data) {
        return count("aula.data = ?1", data);
    }

    /** Total de visitantes por turma numa data. */
    public long contarPorClasseEData(Long classeId, LocalDate data) {
        return count("aula.classe.id = ?1 and aula.data = ?2", classeId, data);
    }
}
