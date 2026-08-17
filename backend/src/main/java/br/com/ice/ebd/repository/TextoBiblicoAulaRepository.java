package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.DiaSemanaLeitura;
import br.com.ice.ebd.model.TextoBiblicoAula;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class TextoBiblicoAulaRepository implements PanacheRepository<TextoBiblicoAula> {

    public List<TextoBiblicoAula> listarPorAula(Long aulaId) {
        return list("aula.id = ?1", aulaId);
    }

    /**
     * Leituras que devem ser enviadas <b>hoje</b>: as do dia da semana de hoje cujas aulas
     * acontecem nos próximos 7 dias (a leitura pertence à semana que antecede a aula), com a
     * aula ainda válida (não adiada) e sem envio registrado hoje (dedup).
     *
     * <p>Para uma data {@code hoje}, existe exatamente uma ocorrência do dia da semana de hoje
     * na janela que antecede cada aula entre {@code hoje+1} e {@code hoje+7} — por isso o
     * intervalo já resolve o casamento leitura ↔ dia, sem aritmética adicional.
     */
    public List<TextoBiblicoAula> paraEnviarEm(LocalDate hoje) {
        return getEntityManager().createQuery(
                        "select t from TextoBiblicoAula t "
                                + "join fetch t.aula a join fetch a.classe "
                                + "where t.diaSemana = :dia and a.adiada = false "
                                + "and a.data between :de and :ate "
                                + "and (t.enviadoEm is null or t.enviadoEm <> :hoje) "
                                + "order by a.data, t.id", TextoBiblicoAula.class)
                .setParameter("dia", DiaSemanaLeitura.de(hoje.getDayOfWeek()))
                .setParameter("de", hoje.plusDays(1))
                .setParameter("ate", hoje.plusDays(7))
                .setParameter("hoje", hoje)
                .getResultList();
    }
}
