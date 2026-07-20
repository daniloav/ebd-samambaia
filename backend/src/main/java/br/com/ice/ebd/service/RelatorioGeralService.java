package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.RelatorioGeralResponse;
import br.com.ice.ebd.dto.RelatorioGeralResponse.LinhaTurma;
import br.com.ice.ebd.dto.RelatorioGeralResponse.Totais;
import br.com.ice.ebd.model.Aula;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Relatório geral consolidado de um dia: totais de presentes, faltosos, bíblias,
 * revistas, lições e visitantes de TODAS as turmas (visão da superintendência).
 * Sem escopo por classe — restrito a ADMIN no resource.
 */
@ApplicationScoped
public class RelatorioGeralService {

    @Inject EntityManager em;

    public RelatorioGeralResponse gerarDoDia(LocalDate data) {
        LocalDate dia = data != null ? data : LocalDate.now();

        List<Aula> aulas = em.createQuery(
                        "select a from Aula a where a.data = :d order by a.classe.nome", Aula.class)
                .setParameter("d", dia).getResultList();

        // Agregados de presença por aula: [presentes, faltosos, biblias, revistas, licoes]
        Map<Long, long[]> presencaPorAula = new HashMap<>();
        for (Object[] r : em.createQuery(
                "select p.aula.id, "
                        + "sum(case when p.presente = true then 1 else 0 end), "
                        + "sum(case when p.presente = false then 1 else 0 end), "
                        + "sum(case when p.trouxeBiblia = true then 1 else 0 end), "
                        + "sum(case when p.trouxeRevista = true then 1 else 0 end), "
                        + "sum(case when p.estudouLicao = true then 1 else 0 end) "
                        + "from Presenca p where p.aula.data = :d group by p.aula.id", Object[].class)
                .setParameter("d", dia).getResultList()) {
            presencaPorAula.put(num(r[0]),
                    new long[]{num(r[1]), num(r[2]), num(r[3]), num(r[4]), num(r[5])});
        }

        // Visitantes por aula (do cadastro de visitantes).
        Map<Long, Long> visitantesPorAula = new HashMap<>();
        for (Object[] r : em.createQuery(
                "select v.aula.id, count(v) from Visitante v where v.aula.data = :d group by v.aula.id", Object[].class)
                .setParameter("d", dia).getResultList()) {
            visitantesPorAula.put(num(r[0]), (long) num(r[1]));
        }

        List<LinhaTurma> turmas = new ArrayList<>();
        long tp = 0, tf = 0, tb = 0, tr = 0, tl = 0, tv = 0;
        for (Aula a : aulas) {
            long[] g = presencaPorAula.getOrDefault(a.getId(), new long[]{0, 0, 0, 0, 0});
            long vis = visitantesPorAula.getOrDefault(a.getId(), 0L);
            turmas.add(new LinhaTurma(
                    a.getClasse().getId(), a.getClasse().getNome(), a.getTema(),
                    g[0], g[1], g[2], g[3], g[4], vis));
            tp += g[0]; tf += g[1]; tb += g[2]; tr += g[3]; tl += g[4]; tv += vis;
        }

        return new RelatorioGeralResponse(dia, turmas.size(),
                new Totais(tp, tf, tb, tr, tl, tv), turmas);
    }

    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
