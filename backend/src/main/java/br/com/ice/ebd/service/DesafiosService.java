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

    private static final int TOP_N = 5;

    @Inject EntityManager em;
    @Inject AlunoRepository alunoRepository;

    /** Métricas de presença acumuladas por aluno. */
    private record MetricasPresenca(long presencas, long biblia, long revista, long licao) {}

    public DesafiosResponse gerar() {
        Map<Long, String> nomes = new LinkedHashMap<>();
        for (Aluno a : alunoRepository.listarAtivos()) {
            nomes.put(a.getId(), a.getNome());
        }

        long totalAulas = ((Number) em.createQuery("select count(a) from Aula a").getSingleResult()).longValue();
        long totalProvas = ((Number) em.createQuery("select count(p) from Prova p").getSingleResult()).longValue();

        Map<Long, MetricasPresenca> presenca = carregarPresencas();
        Map<Long, Double> medias = carregarMediasNotas();

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

        List<RankingItem> melhoresNotas = rankingNotas(nomes, medias);

        return new DesafiosResponse(totalAulas, totalProvas,
                menosFaltou, maisBiblia, maisRevista, maisLicao, melhoresNotas);
    }

    private Map<Long, MetricasPresenca> carregarPresencas() {
        Map<Long, MetricasPresenca> mapa = new LinkedHashMap<>();
        List<Object[]> linhas = em.createQuery(
                        "select p.aluno.id, " +
                        "sum(case when p.presente = true then 1 else 0 end), " +
                        "sum(case when p.trouxeBiblia = true then 1 else 0 end), " +
                        "sum(case when p.trouxeRevista = true then 1 else 0 end), " +
                        "sum(case when p.estudouLicao = true then 1 else 0 end) " +
                        "from Presenca p group by p.aluno.id", Object[].class)
                .getResultList();
        for (Object[] l : linhas) {
            mapa.put((Long) l[0], new MetricasPresenca(
                    toLong(l[1]), toLong(l[2]), toLong(l[3]), toLong(l[4])));
        }
        return mapa;
    }

    private Map<Long, Double> carregarMediasNotas() {
        Map<Long, Double> mapa = new LinkedHashMap<>();
        List<Object[]> linhas = em.createQuery(
                        "select n.aluno.id, avg(n.nota) from NotaProva n group by n.aluno.id", Object[].class)
                .getResultList();
        for (Object[] l : linhas) {
            double media = Math.round(((Number) l[1]).doubleValue() * 100.0) / 100.0;
            mapa.put((Long) l[0], media);
        }
        return mapa;
    }

    /** Monta um ranking de presença ordenado do maior valor para o menor. */
    private List<RankingItem> ranking(Map<Long, String> nomes,
                                      Map<Long, MetricasPresenca> presenca,
                                      Function<MetricasPresenca, Double> extrator,
                                      Function<Double, String> detalhe,
                                      boolean ocultarZeros) {
        record Par(Long id, double valor) {}
        List<Par> pares = new ArrayList<>();
        for (Map.Entry<Long, String> e : nomes.entrySet()) {
            MetricasPresenca m = presenca.getOrDefault(e.getKey(), new MetricasPresenca(0, 0, 0, 0));
            double valor = extrator.apply(m);
            if (ocultarZeros && valor <= 0) {
                continue;
            }
            pares.add(new Par(e.getKey(), valor));
        }
        pares.sort(Comparator.comparingDouble(Par::valor).reversed()
                .thenComparing(p -> nomes.get(p.id())));

        List<RankingItem> ranking = new ArrayList<>();
        int pos = 1;
        for (Par p : pares) {
            if (pos > TOP_N) break;
            ranking.add(new RankingItem(pos++, p.id(), nomes.get(p.id()),
                    p.valor(), detalhe.apply(p.valor())));
        }
        return ranking;
    }

    private List<RankingItem> rankingNotas(Map<Long, String> nomes, Map<Long, Double> medias) {
        record Par(Long id, double media) {}
        List<Par> pares = new ArrayList<>();
        for (Map.Entry<Long, Double> e : medias.entrySet()) {
            if (nomes.containsKey(e.getKey())) {
                pares.add(new Par(e.getKey(), e.getValue()));
            }
        }
        pares.sort(Comparator.comparingDouble(Par::media).reversed()
                .thenComparing(p -> nomes.get(p.id())));

        List<RankingItem> ranking = new ArrayList<>();
        int pos = 1;
        for (Par p : pares) {
            if (pos > TOP_N) break;
            ranking.add(new RankingItem(pos++, p.id(), nomes.get(p.id()),
                    p.media(), String.format("média %.2f", p.media())));
        }
        return ranking;
    }

    private static long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
