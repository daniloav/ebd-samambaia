package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.DesafiosResponse;
import br.com.ice.ebd.dto.RankingItem;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.repository.AlunoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@ApplicationScoped
public class DesafiosService {

    @Inject EscopoService escopo;

    @Inject EntityManager em;
    @Inject AlunoRepository alunoRepository;

    /** Métricas de presença acumuladas por aluno. */
    private record MetricasPresenca(long presencas, long biblia, long revista, long licao, long visitante) {}

    private static final MetricasPresenca ZERO = new MetricasPresenca(0, 0, 0, 0, 0);

    public DesafiosResponse gerar(Long classeId) {
        escopo.assertClasse(classeId);
        long cid = classeId != null ? classeId : -1L;
        Map<Long, String> nomes = new LinkedHashMap<>();
        var alunos = classeId != null ? alunoRepository.listarAtivosPorClasse(classeId)
                                       : alunoRepository.listarAtivos();
        for (Aluno a : alunos) {
            nomes.put(a.getId(), a.getNome());
        }

        long totalAulas = ((Number) em.createQuery("select count(a) from Aula a where (:cid = -1 or a.classe.id = :cid)").setParameter("cid", cid).getSingleResult()).longValue();
        long totalProvas = ((Number) em.createQuery("select count(p) from Prova p where (:cid = -1 or p.classe.id = :cid)").setParameter("cid", cid).getSingleResult()).longValue();

        Map<Long, MetricasPresenca> presenca = carregarPresencas(cid);
        Map<Long, Double> medias = carregarMediasNotas(cid);

        List<RankingItem> menosFaltou = ranking(nomes, presenca,
                m -> (double) m.presencas(),
                valor -> String.format("%d presença(s) de %d aula(s)", valor.longValue(), totalAulas),
                false);

        List<RankingItem> maisBiblia = ranking(nomes, presenca,
                m -> (double) m.biblia(),
                v -> String.format("trouxe a Bíblia %d vez(es)", v.longValue()),
                true);

        List<RankingItem> maisRevista = ranking(nomes, presenca,
                m -> (double) m.revista(),
                v -> String.format("trouxe a revista %d vez(es)", v.longValue()),
                true);

        List<RankingItem> maisLicao = ranking(nomes, presenca,
                m -> (double) m.licao(),
                v -> String.format("estudou a lição %d vez(es)", v.longValue()),
                true);

        List<RankingItem> maisVisitante = ranking(nomes, presenca,
                m -> (double) m.visitante(),
                v -> String.format("trouxe %d visitante(s)", v.longValue()),
                true);

        List<RankingItem> melhoresNotas = rankingNotas(nomes, medias, presenca);

        Map<Long, Double> notasPontos = carregarNotasPontos(cid);
        List<RankingItem> classificacaoGeral = classificacaoGeral(nomes, presenca, notasPontos);

        return new DesafiosResponse(totalAulas, totalProvas,
                menosFaltou, maisBiblia, maisRevista, maisLicao, maisVisitante,
                melhoresNotas, classificacaoGeral);
    }

    /**
     * Desempate por peso (tudo decrescente): lição > visitante > presença > Bíblia > revista.
     * Retorna 0 quando todos os quesitos são iguais (empate total → mesma classificação).
     */
    private static int compararDesempate(MetricasPresenca a, MetricasPresenca b) {
        int c;
        if ((c = Long.compare(b.licao(), a.licao())) != 0) return c;
        if ((c = Long.compare(b.visitante(), a.visitante())) != 0) return c;
        if ((c = Long.compare(b.presencas(), a.presencas())) != 0) return c;
        if ((c = Long.compare(b.biblia(), a.biblia())) != 0) return c;
        return Long.compare(b.revista(), a.revista());
    }

    private Map<Long, MetricasPresenca> carregarPresencas(long cid) {
        Map<Long, MetricasPresenca> mapa = new LinkedHashMap<>();
        List<Object[]> linhas = em.createQuery(
                        "select p.aluno.id, " +
                        "sum(case when p.presente = true then 1 else 0 end), " +
                        "sum(case when p.trouxeBiblia = true then 1 else 0 end), " +
                        "sum(case when p.trouxeRevista = true then 1 else 0 end), " +
                        "sum(case when p.estudouLicao = true then 1 else 0 end) " +
                        "from Presenca p where (:cid = -1 or p.aula.classe.id = :cid) group by p.aluno.id", Object[].class)
                .setParameter("cid", cid)
                .getResultList();
        Map<Long, Long> visitantes = carregarVisitantesPorAluno(cid);
        for (Object[] l : linhas) {
            Long alunoId = (Long) l[0];
            mapa.put(alunoId, new MetricasPresenca(
                    toLong(l[1]), toLong(l[2]), toLong(l[3]), toLong(l[4]),
                    visitantes.getOrDefault(alunoId, 0L)));
        }
        return mapa;
    }

    /** Visitantes trazidos por aluno — fonte única: cadastro de visitantes. */
    private Map<Long, Long> carregarVisitantesPorAluno(long cid) {
        Map<Long, Long> mapa = new LinkedHashMap<>();
        List<Object[]> linhas = em.createQuery(
                        "select v.trazidoPor.id, count(v) from Visitante v "
                        + "where v.trazidoPor is not null and (:cid = -1 or v.aula.classe.id = :cid) "
                        + "group by v.trazidoPor.id", Object[].class)
                .setParameter("cid", cid)
                .getResultList();
        for (Object[] l : linhas) {
            mapa.put((Long) l[0], toLong(l[1]));
        }
        return mapa;
    }

    private Map<Long, Double> carregarMediasNotas(long cid) {
        Map<Long, Double> mapa = new LinkedHashMap<>();
        List<Object[]> linhas = em.createQuery(
                        "select n.aluno.id, avg(n.nota) from NotaProva n where (:cid = -1 or n.prova.classe.id = :cid) group by n.aluno.id", Object[].class)
                .setParameter("cid", cid)
                .getResultList();
        for (Object[] l : linhas) {
            double media = Math.round(((Number) l[1]).doubleValue() * 100.0) / 100.0;
            mapa.put((Long) l[0], media);
        }
        return mapa;
    }

    /**
     * Ranking de um quesito de presença: ordena por valor (desc) e, no empate, pelo desempate
     * por peso. Empate total → mesma posição. Lista completa (sem corte); {@code ocultarZeros}
     * remove quem tem 0 no quesito.
     */
    private List<RankingItem> ranking(Map<Long, String> nomes,
                                      Map<Long, MetricasPresenca> presenca,
                                      Function<MetricasPresenca, Double> extrator,
                                      Function<Double, String> detalhe,
                                      boolean ocultarZeros) {
        record Par(Long id, double valor, MetricasPresenca m) {}
        Comparator<Par> cmp = Comparator
                .comparingDouble((Par p) -> p.valor()).reversed()
                .thenComparing((Par a, Par b) -> compararDesempate(a.m(), b.m()));

        List<Par> pares = new ArrayList<>();
        for (Map.Entry<Long, String> e : nomes.entrySet()) {
            MetricasPresenca m = presenca.getOrDefault(e.getKey(), ZERO);
            double valor = extrator.apply(m);
            if (ocultarZeros && valor <= 0) {
                continue;
            }
            pares.add(new Par(e.getKey(), valor, m));
        }
        pares.sort(cmp);

        List<RankingItem> out = new ArrayList<>();
        int pos = 0;
        for (int i = 0; i < pares.size(); i++) {
            if (i == 0 || cmp.compare(pares.get(i - 1), pares.get(i)) != 0) {
                pos = i + 1;
            }
            Par p = pares.get(i);
            out.add(new RankingItem(pos, p.id(), nomes.get(p.id()), p.valor(), detalhe.apply(p.valor())));
        }
        return out;
    }

    /** Ranking de notas: média (desc), desempate por peso de presença; empate total → mesma posição. */
    private List<RankingItem> rankingNotas(Map<Long, String> nomes, Map<Long, Double> medias,
                                           Map<Long, MetricasPresenca> presenca) {
        record Par(Long id, double media, MetricasPresenca m) {}
        Comparator<Par> cmp = Comparator
                .comparingDouble((Par p) -> p.media()).reversed()
                .thenComparing((Par a, Par b) -> compararDesempate(a.m(), b.m()));

        List<Par> pares = new ArrayList<>();
        for (Map.Entry<Long, Double> e : medias.entrySet()) {
            if (nomes.containsKey(e.getKey())) {
                pares.add(new Par(e.getKey(), e.getValue(), presenca.getOrDefault(e.getKey(), ZERO)));
            }
        }
        pares.sort(cmp);

        List<RankingItem> out = new ArrayList<>();
        int pos = 0;
        for (int i = 0; i < pares.size(); i++) {
            if (i == 0 || cmp.compare(pares.get(i - 1), pares.get(i)) != 0) {
                pos = i + 1;
            }
            Par p = pares.get(i);
            out.add(new RankingItem(pos, p.id(), nomes.get(p.id()), p.media(),
                    String.format("média %.2f", p.media())));
        }
        return out;
    }

    /** Pontos de notas por aluno: soma de (nota/notaMaxima) * 5 sobre as provas. */
    private Map<Long, Double> carregarNotasPontos(long cid) {
        Map<Long, Double> mapa = new LinkedHashMap<>();
        List<Object[]> linhas = em.createQuery(
                        "select n.aluno.id, sum(n.nota / n.prova.notaMaxima) from NotaProva n where (:cid = -1 or n.prova.classe.id = :cid) group by n.aluno.id",
                        Object[].class)
                .setParameter("cid", cid)
                .getResultList();
        for (Object[] l : linhas) {
            mapa.put((Long) l[0], ((Number) l[1]).doubleValue() * 5.0);
        }
        return mapa;
    }

    /**
     * Classificação geral (lista completa): soma dos pontos de todos os quesitos.
     * 1 pt por presença/Bíblia/revista/lição, 2 pts por visitante, + pontos de notas.
     * Empate no total → desempate por peso e, por fim, pelos pontos de notas;
     * empate total → mesma classificação.
     */
    private List<RankingItem> classificacaoGeral(Map<Long, String> nomes,
                                                 Map<Long, MetricasPresenca> presenca,
                                                 Map<Long, Double> notasPontos) {
        record Par(Long id, double total, long pres, long vis, double notas, MetricasPresenca m) {}
        Comparator<Par> cmp = Comparator
                .comparingDouble((Par p) -> p.total()).reversed()
                .thenComparing((Par a, Par b) -> compararDesempate(a.m(), b.m()))
                .thenComparing(Comparator.comparingDouble((Par p) -> p.notas()).reversed());

        List<Par> pares = new ArrayList<>();
        for (Map.Entry<Long, String> e : nomes.entrySet()) {
            MetricasPresenca m = presenca.getOrDefault(e.getKey(), ZERO);
            double notas = notasPontos.getOrDefault(e.getKey(), 0.0);
            double total = m.presencas() + m.biblia() + m.revista() + m.licao()
                    + 2.0 * m.visitante() + notas;
            total = Math.round(total * 10.0) / 10.0;
            pares.add(new Par(e.getKey(), total, m.presencas(), m.visitante(), Math.round(notas * 10.0) / 10.0, m));
        }
        pares.sort(cmp);

        List<RankingItem> out = new ArrayList<>();
        int pos = 0;
        for (int i = 0; i < pares.size(); i++) {
            if (i == 0 || cmp.compare(pares.get(i - 1), pares.get(i)) != 0) {
                pos = i + 1;
            }
            Par p = pares.get(i);
            String det = String.format("%d presença(s) · %d visitante(s) · %.0f pts de notas",
                    p.pres(), p.vis(), p.notas());
            out.add(new RankingItem(pos, p.id(), nomes.get(p.id()), p.total(), det));
        }
        return out;
    }

    private static long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
