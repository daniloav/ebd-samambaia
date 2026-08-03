package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.RelatorioPresencaItem;
import br.com.ice.ebd.dto.RelatorioPresencaResponse;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.repository.AlunoRepository;
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

    @Inject EscopoService escopo;
    @Inject EntityManager em;
    @Inject AlunoRepository alunoRepository;

    /** Totais somados por aluno no período (visitante vem do cadastro de visitantes). */
    private record Totais(long presencas, long biblia, long revista, long licao) {}

    public RelatorioPresencaResponse gerar(LocalDate inicio, LocalDate fim, Long classeId) {
        escopo.assertClasse(classeId);
        LocalDate ini = inicio != null ? inicio : LocalDate.of(2000, 1, 1);
        LocalDate f = fim != null ? fim : LocalDate.now();
        long cid = classeId != null ? classeId : -1L;

        long totalAulas = em.createQuery(
                        "select count(a) from Aula a where a.adiada = false and a.data between :ini and :fim and (:cid = -1 or a.classe.id = :cid)", Long.class)
                .setParameter("ini", ini).setParameter("fim", f).setParameter("cid", cid)
                .getSingleResult();

        Map<Long, Totais> porAluno = new HashMap<>();
        for (Object[] l : em.createQuery(
                        "select p.aluno.id, "
                        + "sum(case when p.presente = true then 1 else 0 end), "
                        + "sum(case when p.trouxeBiblia = true then 1 else 0 end), "
                        + "sum(case when p.trouxeRevista = true then 1 else 0 end), "
                        + "sum(case when p.estudouLicao = true then 1 else 0 end) "
                        + "from Presenca p where p.aula.adiada = false and p.aula.data between :ini and :fim and (:cid = -1 or p.aula.classe.id = :cid) "
                        + "group by p.aluno.id", Object[].class)
                .setParameter("ini", ini).setParameter("fim", f).setParameter("cid", cid)
                .getResultList()) {
            porAluno.put((Long) l[0], new Totais(toLong(l[1]), toLong(l[2]), toLong(l[3]), toLong(l[4])));
        }

        // Visitantes trazidos por aluno no período — fonte única: cadastro de visitantes.
        Map<Long, Long> visitantes = new HashMap<>();
        for (Object[] l : em.createQuery(
                        "select v.trazidoPor.id, count(v) from Visitante v where v.trazidoPor is not null "
                        + "and v.aula.adiada = false and v.aula.data between :ini and :fim and (:cid = -1 or v.aula.classe.id = :cid) "
                        + "group by v.trazidoPor.id", Object[].class)
                .setParameter("ini", ini).setParameter("fim", f).setParameter("cid", cid)
                .getResultList()) {
            visitantes.put((Long) l[0], toLong(l[1]));
        }

        List<RelatorioPresencaItem> itens = new ArrayList<>();
        for (Aluno aluno : (classeId != null ? alunoRepository.listarAtivosPorClasse(classeId) : alunoRepository.listarAtivos())) {
            Totais t = porAluno.getOrDefault(aluno.getId(), new Totais(0, 0, 0, 0));
            long presencas = t.presencas();
            long faltas = Math.max(0, totalAulas - presencas);
            double percentual = totalAulas > 0
                    ? Math.round((presencas * 10000.0 / totalAulas)) / 100.0
                    : 0.0;
            itens.add(new RelatorioPresencaItem(aluno.getId(), aluno.getNome(),
                    totalAulas, presencas, faltas, percentual,
                    t.biblia(), t.revista(), t.licao(), visitantes.getOrDefault(aluno.getId(), 0L)));
        }

        return new RelatorioPresencaResponse(ini, f, totalAulas, itens);
    }

    private static long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
