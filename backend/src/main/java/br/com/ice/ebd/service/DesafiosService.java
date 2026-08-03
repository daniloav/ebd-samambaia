package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.DesafiosResponse;
import br.com.ice.ebd.dto.RankingItem;
import br.com.ice.ebd.dto.MeuRankingResponse;
import br.com.ice.ebd.dto.RankingTurmasResponse;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.ClasseRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

@ApplicationScoped
public class DesafiosService {

    @Inject EscopoService escopo;

    @Inject EntityManager em;
    @Inject AlunoRepository alunoRepository;
    @Inject ClasseRepository classeRepository;

    /** Métricas de presença acumuladas por aluno. */
    private record MetricasPresenca(long presencas, long biblia, long revista, long licao, long visitante, long justificadas) {}

    private static final MetricasPresenca ZERO = new MetricasPresenca(0, 0, 0, 0, 0, 0);

    /** Métricas de presença somadas por TURMA (mesmos quesitos, agregados por classe). */
    private record MetricasTurma(long presencas, long biblia, long revista, long licao, long justificadas) {}

    private static final MetricasTurma ZERO_TURMA = new MetricasTurma(0, 0, 0, 0, 0);

    public DesafiosResponse gerar(Long classeId, Integer ano, Integer trimestre) {
        escopo.assertClasse(classeId);
        PeriodoLetivo p = PeriodoLetivo.deOuTudo(ano, trimestre);
        return montar(classeId, p.inicio(), p.fim());
    }

    /** Ranking resumido do aluno logado (pódio + posição dele) — para o /api/me/ranking. */
    @Transactional
    public MeuRankingResponse resumoDoAluno() {
        Long alunoId = escopo.alunoIdLogado();
        if (alunoId == null) {
            throw new ForbiddenException("Seu usuário não está vinculado a um aluno.");
        }
        Aluno aluno = alunoRepository.findById(alunoId);
        if (aluno == null || aluno.getClasse() == null) {
            throw new NotFoundException("Aluno não encontrado.");
        }
        PeriodoLetivo tudo = PeriodoLetivo.deOuTudo(null, null);
        List<RankingItem> geral = montar(aluno.getClasse().getId(), tudo.inicio(), tudo.fim())
                .classificacaoGeral();
        List<MeuRankingResponse.Item> podio = geral.stream().limit(3)
                .map(r -> itemResumo(r, alunoId)).toList();
        MeuRankingResponse.Item minha = geral.stream().filter(r -> alunoId.equals(r.alunoId()))
                .findFirst().map(r -> itemResumo(r, alunoId)).orElse(null);
        return new MeuRankingResponse(aluno.getClasse().getNome(), geral.size(), podio, minha);
    }

    private static MeuRankingResponse.Item itemResumo(RankingItem r, Long meuId) {
        return new MeuRankingResponse.Item(r.posicao(), r.alunoId(), r.nome(), r.valor(),
                r.detalhe(), meuId.equals(r.alunoId()));
    }

    private DesafiosResponse montar(Long classeId, LocalDate ini, LocalDate fim) {
        long cid = classeId != null ? classeId : -1L;
        Map<Long, String> nomes = new LinkedHashMap<>();
        var alunos = classeId != null ? alunoRepository.listarAtivosPorClasse(classeId)
                                       : alunoRepository.listarAtivos();
        for (Aluno a : alunos) {
            nomes.put(a.getId(), a.getNome());
        }

        LocalDate hoje = LocalDate.now();
        long totalAulas = ((Number) em.createQuery("select count(a) from Aula a where a.adiada = false and (:cid = -1 or a.classe.id = :cid) and a.data <= :hoje and a.data between :ini and :fim").setParameter("cid", cid).setParameter("hoje", hoje).setParameter("ini", ini).setParameter("fim", fim).getSingleResult()).longValue();
        long totalProvas = ((Number) em.createQuery("select count(p) from Prova p where (:cid = -1 or p.classe.id = :cid) and p.data between :ini and :fim").setParameter("cid", cid).setParameter("ini", ini).setParameter("fim", fim).getSingleResult()).longValue();

        Map<Long, MetricasPresenca> presenca = carregarPresencas(cid, ini, fim);
        Map<Long, Double> medias = carregarMediasNotas(cid, ini, fim);

        List<RankingItem> menosFaltou = ranking(nomes, presenca,
                m -> arred1(m.presencas() + 0.3 * m.justificadas()),
                (valor, m) -> m.justificadas() > 0
                        ? String.format("%d presença(s) + %d falta(s) justificada(s) de %d aula(s)",
                                m.presencas(), m.justificadas(), totalAulas)
                        : String.format("%d presença(s) de %d aula(s)", m.presencas(), totalAulas),
                false);

        List<RankingItem> maisBiblia = ranking(nomes, presenca,
                m -> (double) m.biblia(),
                (v, m) -> String.format("trouxe a Bíblia %d vez(es)", v.longValue()),
                true);

        List<RankingItem> maisRevista = ranking(nomes, presenca,
                m -> (double) m.revista(),
                (v, m) -> String.format("trouxe a revista %d vez(es)", v.longValue()),
                true);

        List<RankingItem> maisLicao = ranking(nomes, presenca,
                m -> (double) m.licao(),
                (v, m) -> String.format("estudou a lição %d vez(es)", v.longValue()),
                true);

        List<RankingItem> maisVisitante = ranking(nomes, presenca,
                m -> (double) m.visitante(),
                (v, m) -> String.format("trouxe %d visitante(s)", v.longValue()),
                true);

        List<RankingItem> melhoresNotas = rankingNotas(nomes, medias, presenca);

        Map<Long, Double> notasPontos = carregarNotasPontos(cid, ini, fim);
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

    private Map<Long, MetricasPresenca> carregarPresencas(long cid, LocalDate ini, LocalDate fim) {
        Map<Long, MetricasPresenca> mapa = new LinkedHashMap<>();
        List<Object[]> linhas = em.createQuery(
                        "select p.aluno.id, " +
                        "sum(case when p.presente = true then 1 else 0 end), " +
                        "sum(case when p.trouxeBiblia = true then 1 else 0 end), " +
                        "sum(case when p.trouxeRevista = true then 1 else 0 end), " +
                        "sum(case when p.estudouLicao = true then 1 else 0 end), " +
                        "sum(case when p.presente = false and p.justificada = true then 1 else 0 end) " +
                        "from Presenca p join p.aula aula "
                        + "left join aula.professor prof left join prof.aluno profAluno "
                        + "where aula.adiada = false and (:cid = -1 or aula.classe.id = :cid) and aula.data <= :hoje "
                        + "and aula.data between :ini and :fim "
                        + "and (profAluno is null or profAluno.id <> p.aluno.id) "
                        + "group by p.aluno.id", Object[].class)
                .setParameter("cid", cid)
                .setParameter("hoje", LocalDate.now())
                .setParameter("ini", ini).setParameter("fim", fim)
                .getResultList();
        Map<Long, Long> visitantes = carregarVisitantesPorAluno(cid, ini, fim);
        for (Object[] l : linhas) {
            Long alunoId = (Long) l[0];
            mapa.put(alunoId, new MetricasPresenca(
                    toLong(l[1]), toLong(l[2]), toLong(l[3]), toLong(l[4]),
                    visitantes.getOrDefault(alunoId, 0L), toLong(l[5])));
        }
        return mapa;
    }

    /** Visitantes trazidos por aluno — fonte única: cadastro de visitantes. */
    private Map<Long, Long> carregarVisitantesPorAluno(long cid, LocalDate ini, LocalDate fim) {
        Map<Long, Long> mapa = new LinkedHashMap<>();
        List<Object[]> linhas = em.createQuery(
                        "select v.trazidoPor.id, count(v) from Visitante v join v.aula aula "
                        + "left join aula.professor prof left join prof.aluno profAluno "
                        + "where v.trazidoPor is not null and aula.adiada = false and (:cid = -1 or aula.classe.id = :cid) "
                        + "and aula.data <= :hoje and aula.data between :ini and :fim "
                        + "and (profAluno is null or profAluno.id <> v.trazidoPor.id) "
                        + "group by v.trazidoPor.id", Object[].class)
                .setParameter("cid", cid)
                .setParameter("hoje", LocalDate.now())
                .setParameter("ini", ini).setParameter("fim", fim)
                .getResultList();
        for (Object[] l : linhas) {
            mapa.put((Long) l[0], toLong(l[1]));
        }
        return mapa;
    }

    private Map<Long, Double> carregarMediasNotas(long cid, LocalDate ini, LocalDate fim) {
        Map<Long, Double> mapa = new LinkedHashMap<>();
        List<Object[]> linhas = em.createQuery(
                        "select n.aluno.id, avg(n.nota) from NotaProva n where (:cid = -1 or n.prova.classe.id = :cid) and n.prova.data between :ini and :fim group by n.aluno.id", Object[].class)
                .setParameter("cid", cid)
                .setParameter("ini", ini).setParameter("fim", fim)
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
                                      BiFunction<Double, MetricasPresenca, String> detalhe,
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
            out.add(new RankingItem(pos, p.id(), nomes.get(p.id()), p.valor(), detalhe.apply(p.valor(), p.m())));
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
    private Map<Long, Double> carregarNotasPontos(long cid, LocalDate ini, LocalDate fim) {
        Map<Long, Double> mapa = new LinkedHashMap<>();
        List<Object[]> linhas = em.createQuery(
                        "select n.aluno.id, sum(n.nota / n.prova.notaMaxima) from NotaProva n where (:cid = -1 or n.prova.classe.id = :cid) and n.prova.data between :ini and :fim group by n.aluno.id",
                        Object[].class)
                .setParameter("cid", cid)
                .setParameter("ini", ini).setParameter("fim", fim)
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
            double total = m.presencas() + 0.3 * m.justificadas() + m.biblia() + m.revista() + m.licao()
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
            String just = p.m().justificadas() > 0
                    ? " (+" + p.m().justificadas() + " just.)" : "";
            String det = String.format("%d presença(s)%s · %d visitante(s) · %.0f pts de notas",
                    p.pres(), just, p.vis(), p.notas());
            out.add(new RankingItem(pos, p.id(), nomes.get(p.id()), p.total(), det));
        }
        return out;
    }

    // ===================== Ranking por turma (desafio entre classes) =====================

    /** Ranking das turmas no escopo do usuário (ADMIN = todas; PROFESSOR = as dele). */
    public RankingTurmasResponse gerarPorTurma(Integer ano, Integer trimestre) {
        Set<Long> permitidas = escopo.classesPermitidas(); // null = todas (ADMIN)
        List<Classe> classes = classeRepository.listarAtivas();
        if (permitidas != null) {
            classes = classes.stream().filter(c -> permitidas.contains(c.getId())).toList();
        }
        return montarPorTurma(classes, ano, trimestre, null);
    }

    /** Ranking de todas as turmas para o aluno logado, destacando a turma dele. */
    @Transactional
    public RankingTurmasResponse resumoTurmasDoAluno() {
        Long alunoId = escopo.alunoIdLogado();
        Long minhaClasseId = null;
        if (alunoId != null) {
            Aluno aluno = alunoRepository.findById(alunoId);
            if (aluno != null && aluno.getClasse() != null) {
                minhaClasseId = aluno.getClasse().getId();
            }
        }
        return montarPorTurma(classeRepository.listarAtivas(), null, null, minhaClasseId);
    }

    private RankingTurmasResponse montarPorTurma(List<Classe> classes, Integer ano, Integer trimestre, Long minhaClasseId) {
        PeriodoLetivo p = PeriodoLetivo.deOuTudo(ano, trimestre);
        LocalDate ini = p.inicio();
        LocalDate fim = p.fim();
        LocalDate hoje = LocalDate.now();

        List<Long> ids = classes.stream().map(Classe::getId).toList();
        long totalAulas = 0;
        long totalProvas = 0;
        if (!ids.isEmpty()) {
            totalAulas = ((Number) em.createQuery("select count(a) from Aula a where a.adiada = false and a.classe.id in :ids and a.data <= :hoje and a.data between :ini and :fim")
                    .setParameter("ids", ids).setParameter("hoje", hoje).setParameter("ini", ini).setParameter("fim", fim).getSingleResult()).longValue();
            totalProvas = ((Number) em.createQuery("select count(p) from Prova p where p.classe.id in :ids and p.data between :ini and :fim")
                    .setParameter("ids", ids).setParameter("ini", ini).setParameter("fim", fim).getSingleResult()).longValue();
        }

        Map<Long, MetricasTurma> pres = carregarPresencasPorTurma(ini, fim);
        Map<Long, Long> visitantes = carregarVisitantesPorTurma(ini, fim);
        Map<Long, Double> notas = carregarNotasPontosPorTurma(ini, fim);

        record Par(Long id, String nome, double media, double total, int alunos) {}
        Comparator<Par> cmp = Comparator
                .comparingDouble((Par p2) -> p2.media()).reversed()
                .thenComparing(Comparator.comparingDouble((Par p2) -> p2.total()).reversed())
                .thenComparing(p2 -> p2.nome(), String.CASE_INSENSITIVE_ORDER);

        List<Par> pares = new ArrayList<>();
        for (Classe c : classes) {
            int alunos = alunoRepository.listarAtivosPorClasse(c.getId()).size();
            if (alunos == 0) {
                continue; // turma sem alunos ativos não entra (evita divisão por zero)
            }
            MetricasTurma m = pres.getOrDefault(c.getId(), ZERO_TURMA);
            long vis = visitantes.getOrDefault(c.getId(), 0L);
            double pontosNotas = notas.getOrDefault(c.getId(), 0.0);
            double total = m.presencas() + 0.3 * m.justificadas() + m.biblia() + m.revista() + m.licao()
                    + 2.0 * vis + pontosNotas;
            total = arred1(total);
            double media = Math.round((total / alunos) * 100.0) / 100.0;
            pares.add(new Par(c.getId(), c.getNome(), media, total, alunos));
        }
        pares.sort(cmp);

        List<RankingTurmasResponse.Item> turmas = new ArrayList<>();
        int pos = 0;
        for (int i = 0; i < pares.size(); i++) {
            if (i == 0 || cmp.compare(pares.get(i - 1), pares.get(i)) != 0) {
                pos = i + 1;
            }
            Par par = pares.get(i);
            String det = String.format("%s pts no total · %d aluno(s)", formatarPontos(par.total()), par.alunos());
            turmas.add(new RankingTurmasResponse.Item(pos, par.id(), par.nome(),
                    par.media(), par.total(), par.alunos(), det));
        }
        return new RankingTurmasResponse(totalAulas, totalProvas, minhaClasseId, turmas);
    }

    private Map<Long, MetricasTurma> carregarPresencasPorTurma(LocalDate ini, LocalDate fim) {
        Map<Long, MetricasTurma> mapa = new LinkedHashMap<>();
        List<Object[]> linhas = em.createQuery(
                        "select aula.classe.id, " +
                        "sum(case when p.presente = true then 1 else 0 end), " +
                        "sum(case when p.trouxeBiblia = true then 1 else 0 end), " +
                        "sum(case when p.trouxeRevista = true then 1 else 0 end), " +
                        "sum(case when p.estudouLicao = true then 1 else 0 end), " +
                        "sum(case when p.presente = false and p.justificada = true then 1 else 0 end) " +
                        "from Presenca p join p.aula aula "
                        + "left join aula.professor prof left join prof.aluno profAluno "
                        + "where aula.adiada = false and aula.data <= :hoje and aula.data between :ini and :fim "
                        + "and (profAluno is null or profAluno.id <> p.aluno.id) "
                        + "group by aula.classe.id", Object[].class)
                .setParameter("hoje", LocalDate.now())
                .setParameter("ini", ini).setParameter("fim", fim)
                .getResultList();
        for (Object[] l : linhas) {
            mapa.put((Long) l[0], new MetricasTurma(
                    toLong(l[1]), toLong(l[2]), toLong(l[3]), toLong(l[4]), toLong(l[5])));
        }
        return mapa;
    }

    private Map<Long, Long> carregarVisitantesPorTurma(LocalDate ini, LocalDate fim) {
        Map<Long, Long> mapa = new LinkedHashMap<>();
        List<Object[]> linhas = em.createQuery(
                        "select aula.classe.id, count(v) from Visitante v join v.aula aula "
                        + "left join aula.professor prof left join prof.aluno profAluno "
                        + "where v.trazidoPor is not null and aula.adiada = false and aula.data <= :hoje and aula.data between :ini and :fim "
                        + "and (profAluno is null or profAluno.id <> v.trazidoPor.id) "
                        + "group by aula.classe.id", Object[].class)
                .setParameter("hoje", LocalDate.now())
                .setParameter("ini", ini).setParameter("fim", fim)
                .getResultList();
        for (Object[] l : linhas) {
            mapa.put((Long) l[0], toLong(l[1]));
        }
        return mapa;
    }

    private Map<Long, Double> carregarNotasPontosPorTurma(LocalDate ini, LocalDate fim) {
        Map<Long, Double> mapa = new LinkedHashMap<>();
        List<Object[]> linhas = em.createQuery(
                        "select n.prova.classe.id, sum(n.nota / n.prova.notaMaxima) from NotaProva n where n.prova.data between :ini and :fim group by n.prova.classe.id",
                        Object[].class)
                .setParameter("ini", ini).setParameter("fim", fim)
                .getResultList();
        for (Object[] l : linhas) {
            mapa.put((Long) l[0], ((Number) l[1]).doubleValue() * 5.0);
        }
        return mapa;
    }

    /** Formata pontos com no máximo 1 casa decimal (inteiro sem casa). */
    private static String formatarPontos(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    private static long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    /** Arredonda para 1 casa decimal (usado no valor do ranking com peso fracionário). */
    private static double arred1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
