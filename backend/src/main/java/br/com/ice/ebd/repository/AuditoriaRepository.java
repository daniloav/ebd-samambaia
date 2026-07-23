package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Auditoria;
import br.com.ice.ebd.model.EntidadeAuditoria;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class AuditoriaRepository implements PanacheRepository<Auditoria> {

    /** Últimos registros (desc por data), com filtros opcionais e limite. */
    public List<Auditoria> listar(EntidadeAuditoria entidade, LocalDateTime inicio, LocalDateTime fim, int limite) {
        StringBuilder q = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();
        if (entidade != null) {
            params.add(entidade);
            q.append(" and entidade = ?").append(params.size());
        }
        if (inicio != null) {
            params.add(inicio);
            q.append(" and dataHora >= ?").append(params.size());
        }
        if (fim != null) {
            params.add(fim);
            q.append(" and dataHora <= ?").append(params.size());
        }
        return find(q.toString(), Sort.by("dataHora").descending(), params.toArray())
                .page(Page.ofSize(limite)).firstPage().list();
    }
}
