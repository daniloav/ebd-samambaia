package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.DashboardResponse;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.repository.AlunoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Agrega os números e séries do painel. classeId nulo = todas as turmas (respeitando o escopo). */
@ApplicationScoped
public class DashboardService {

    private static final int MAX_AULAS_GRAFICO = 10;

    @Inject EscopoService escopo;
    @Inject EntityManager em;
    @Inject AlunoRepository alunoRepository;

    public DashboardResponse gerar(Long classeId) {
        escopo.assertClasse(classeId);
        long cid = classeId != null ? classeId : -1L;

        List<Aluno> ativos = classeId != null
                ? alunoRepository.listarAtivosPorClasse(classeId)
                : alunoRepository.listarAtivos();
        long totalAlunos = ativos.size();

        long totalAulas = ((Number) em.createQuery(
                        "select count(a) from Aula a where a.adiada = false and (:cid = -1 or a.classe.id = :cid)")
                .setParameter("cid", cid).getSingleResult()).longValue();
        long totalProvas = ((Number) em.createQuery(
                        "select count(p) from Prova p where (:cid = -1 or p.classe.id = :cid)")
                .setParameter("cid", cid).getSingleResult()).longValue();

        // Frequência por aula (últimas N aulas), presentes / total de alunos ativos.
        List<DashboardResponse.PontoFrequencia> serie = new ArrayList<>();
        List<Object[]> linhas = em.createQuery(
                        "select a.data, a.tema, "
                        + "(select count(pr) from Presenca pr where pr.aula = a and pr.presente = true) "
                        + "from Aula a where a.adiada = false and (:cid = -1 or a.classe.id = :cid) order by a.data desc", Object[].class)
                .setParameter("cid", cid).setMaxResults(MAX_AULAS_GRAFICO).getResultList();
        for (Object[] l : linhas) {
            LocalDate data = (LocalDate) l[0];
            String tema = (String) l[1];
            long presentes = ((Number) l[2]).longValue();
            double pct = totalAlunos > 0 ? Math.round(presentes * 10000.0 / totalAlunos) / 100.0 : 0.0;
            serie.add(new DashboardResponse.PontoFrequencia(data, tema, presentes, totalAlunos, pct));
        }
        Collections.reverse(serie); // cronológico no gráfico

        // Presenças por aluno (para distribuição + presença média).
        Map<Long, Long> presencasPorAluno = new HashMap<>();
        for (Object[] l : em.createQuery(
                        "select p.aluno.id, sum(case when p.presente = true then 1 else 0 end) "
                        + "from Presenca p where p.aula.adiada = false and (:cid = -1 or p.aula.classe.id = :cid) group by p.aluno.id", Object[].class)
                .setParameter("cid", cid).getResultList()) {
            presencasPorAluno.put((Long) l[0], ((Number) l[1]).longValue());
        }

        long excelente = 0, boa = 0, atencao = 0, somaPresencas = 0;
        for (Aluno a : ativos) {
            long pres = presencasPorAluno.getOrDefault(a.getId(), 0L);
            somaPresencas += pres;
            double pct = totalAulas > 0 ? (pres * 100.0 / totalAulas) : 0.0;
            if (pct >= 90) { excelente++; }
            else if (pct >= 75) { boa++; }
            else { atencao++; }
        }
        double presencaMedia = (totalAulas > 0 && totalAlunos > 0)
                ? Math.round(somaPresencas * 10000.0 / (totalAulas * totalAlunos)) / 100.0 : 0.0;

        return new DashboardResponse(totalAlunos, totalAulas, totalProvas, presencaMedia,
                serie, new DashboardResponse.Distribuicao(excelente, boa, atencao));
    }
}
