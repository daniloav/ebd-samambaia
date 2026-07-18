package br.com.icev.ebd.service;

import br.com.icev.ebd.dto.RelatorioPresencaItem;
import br.com.icev.ebd.dto.RelatorioPresencaResponse;
import br.com.icev.ebd.model.Aluno;
import br.com.icev.ebd.repository.AlunoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class RelatorioService {

    @Inject EntityManager em;
    @Inject AlunoRepository alunoRepository;

    /** Guarda os totais somados por aluno no período. */
    private record Totais(long presencas, long biblia, long revista, long licao) {}

    public RelatorioPresencaResponse gerar(LocalDate inicio, LocalDate fim) {
        LocalDate ini = inicio != null ? inicio : LocalDate.of(2000, 1, 1);
        LocalDate f = fim != null ? fim : LocalDate.now();

        long totalAulas = em.createQuery(
                        "select count(a) from Aula a where a.data between :ini and :fim", Long.class)
                .setParameter("ini", ini)
                .setParameter("fim", f)
                .getSingleResult();

        Map<Long, Totais> porAluno = new HashMap<>();
        List<Object[]> linhas = em.createQuery(
                        "select p.aluno.id, " +
                        "sum(case when p.presente = true then 1 else 0 end), " +
                        "sum(case when p.trouxeBiblia = true then 1 else 0 end), " +
                        "sum(case when p.trouxeRevista = true then 1 else 0 end), " +
                        "sum(case when p.estudouLicao = true then 1 else 0 end) " +
                        "from Presenca p where p.aula.data between :ini and :fim " +
                        "group by p.aluno.id", Object[].class)
                .setParameter("ini", ini)
                .setParameter("fim", f)
                .getResultList();

        for (Object[] l : linhas) {
            Long alunoId = (Long) l[0];
            porAluno.put(alunoId, new Totais(
                    toLong(l[1]), toLong(l[2]), toLong(l[3]), toLong(l[4])));
        }

        List<RelatorioPresencaItem> itens = new ArrayList<>();
        for (Aluno aluno : alunoRepository.listarAtivos()) {
            Totais t = porAluno.getOrDefault(aluno.getId(), new Totais(0, 0, 0, 0));
            long presencas = t.presencas();
            long faltas = Math.max(0, totalAulas - presencas);
            double percentual = totalAulas > 0
                    ? Math.round((presencas * 10000.0 / totalAulas)) / 100.0
                    : 0.0;
            itens.add(new RelatorioPresencaItem(aluno.getId(), aluno.getNome(),
                    totalAulas, presencas, faltas, percentual,
                    t.biblia(), t.revista(), t.licao()));
        }

        return new RelatorioPresencaResponse(ini, f, totalAulas, itens);
    }

    private static long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
