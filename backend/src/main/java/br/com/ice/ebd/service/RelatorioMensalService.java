package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.RelatorioMensalResponse;
import br.com.ice.ebd.dto.RelatorioMensalResponse.LinhaTurma;
import br.com.ice.ebd.dto.RelatorioMensalResponse.PontoSerie;
import br.com.ice.ebd.dto.RelatorioMensalResponse.Totais;
import br.com.ice.ebd.dto.RelatorioMensalResponse.TurmaSelecionada;
import br.com.ice.ebd.dto.RelatorioMensalResponse.ValorTurma;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.repository.ClasseRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Relatório geral de presença por mês, consolidando as turmas escolhidas (uma, várias ou
 * todas as permitidas). Além dos totais e da quebra por turma, monta a série do gráfico:
 * um ponto por <b>domingo</b> quando o filtro é um mês, um ponto por <b>mês</b> quando é o
 * ano inteiro.
 *
 * <p>Aula <b>adiada</b> fica fora de tudo (ela não pontua em lugar nenhum). O percentual usa
 * como base os registros da chamada — presenças ÷ (presenças + faltas) —, então uma aula sem
 * chamada não derruba o índice da turma; ela aparece na diferença entre "aulas" e
 * "aulas com chamada".
 */
@ApplicationScoped
public class RelatorioMensalService {

    private static final String[] MESES = {
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"};
    private static final String[] MESES_CURTOS = {
            "Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"};

    @Inject EntityManager em;
    @Inject EscopoService escopo;
    @Inject ClasseRepository classeRepository;

    /** Agregado mutável usado para somar enquanto percorremos as aulas. */
    private static final class Acumulador {
        long aulas, aulasComChamada, presencas, faltas, justificadas, biblias, revistas, licoes, visitantes;

        void somar(long[] g, long vis) {
            aulas++;
            if (g[0] + g[1] > 0) {
                aulasComChamada++;
            }
            presencas += g[0];
            faltas += g[1];
            justificadas += g[2];
            biblias += g[3];
            revistas += g[4];
            licoes += g[5];
            visitantes += vis;
        }

        Totais fechar(long alunosAtivos) {
            return new Totais(aulas, aulasComChamada, alunosAtivos, presencas, faltas, justificadas,
                    percentual(presencas, faltas), biblias, revistas, licoes, visitantes);
        }
    }

    /**
     * Gera o relatório. {@code mes} nulo = ano inteiro; {@code classeIds} vazio/nulo = todas as
     * turmas que o usuário pode ver.
     */
    public RelatorioMensalResponse gerar(Integer ano, Integer mes, List<Long> classeIds) {
        int anoRef = ano != null ? ano : LocalDate.now().getYear();
        if (mes != null && (mes < 1 || mes > 12)) {
            throw new BadRequestException("Mês inválido: " + mes);
        }
        LocalDate inicio = mes != null ? LocalDate.of(anoRef, mes, 1) : LocalDate.of(anoRef, 1, 1);
        LocalDate fim = mes != null ? inicio.withDayOfMonth(inicio.lengthOfMonth()) : LocalDate.of(anoRef, 12, 31);

        List<Classe> turmas = resolverTurmas(classeIds);
        if (turmas.isEmpty()) {
            return vazio(anoRef, mes, inicio, fim);
        }
        Set<Long> ids = new LinkedHashSet<>(turmas.stream().map(Classe::getId).toList());
        Map<Long, String> nomes = new LinkedHashMap<>();
        turmas.forEach(c -> nomes.put(c.getId(), c.getNome()));

        // Aulas válidas do período, em ordem de data: [aulaId, classeId, data]
        List<Object[]> aulas = em.createQuery(
                        "select a.id, a.classe.id, a.data from Aula a "
                                + "where a.adiada = false and a.data between :ini and :fim and a.classe.id in :cids "
                                + "order by a.data, a.classe.nome", Object[].class)
                .setParameter("ini", inicio).setParameter("fim", fim).setParameter("cids", ids)
                .getResultList();

        Map<Long, long[]> presencaPorAula = agregadosDeChamada(inicio, fim, ids);
        Map<Long, Long> visitantesPorAula = visitantesPorAula(inicio, fim, ids);
        Map<Long, Long> alunosPorTurma = alunosAtivosPorTurma(ids);

        Acumulador geral = new Acumulador();
        Map<Long, Acumulador> porTurma = new LinkedHashMap<>();
        Map<String, Acumulador> porPonto = new LinkedHashMap<>();
        Map<String, LocalDate> dataDoPonto = new LinkedHashMap<>();
        Map<String, Map<Long, Acumulador>> porPontoETurma = new LinkedHashMap<>();
        ids.forEach(id -> porTurma.put(id, new Acumulador()));

        for (Object[] a : aulas) {
            Long aulaId = num(a[0]);
            Long classeId = num(a[1]);
            LocalDate data = (LocalDate) a[2];
            long[] g = presencaPorAula.getOrDefault(aulaId, new long[]{0, 0, 0, 0, 0, 0});
            long vis = visitantesPorAula.getOrDefault(aulaId, 0L);

            geral.somar(g, vis);
            porTurma.get(classeId).somar(g, vis);

            // Um ponto por domingo (visão mensal) ou por mês (visão anual).
            String chave = mes != null ? data.toString() : String.valueOf(data.getMonthValue());
            dataDoPonto.putIfAbsent(chave, mes != null ? data : data.withDayOfMonth(1));
            porPonto.computeIfAbsent(chave, k -> new Acumulador()).somar(g, vis);
            porPontoETurma.computeIfAbsent(chave, k -> new LinkedHashMap<>())
                    .computeIfAbsent(classeId, k -> new Acumulador()).somar(g, vis);
        }

        long alunosTotal = alunosPorTurma.values().stream().mapToLong(Long::longValue).sum();

        List<LinhaTurma> linhas = new ArrayList<>();
        for (Long id : ids) {
            linhas.add(new LinhaTurma(id, nomes.get(id),
                    porTurma.get(id).fechar(alunosPorTurma.getOrDefault(id, 0L))));
        }

        List<PontoSerie> serie = new ArrayList<>();
        for (Map.Entry<String, Acumulador> e : porPonto.entrySet()) {
            LocalDate data = dataDoPonto.get(e.getKey());
            List<ValorTurma> valores = new ArrayList<>();
            Map<Long, Acumulador> doPonto = porPontoETurma.getOrDefault(e.getKey(), Map.of());
            for (Long id : ids) {
                Acumulador ac = doPonto.get(id);
                long p = ac != null ? ac.presencas : 0;
                long f = ac != null ? ac.faltas : 0;
                valores.add(new ValorTurma(id, nomes.get(id), p, f, percentual(p, f)));
            }
            serie.add(new PontoSerie(rotulo(data, mes != null), data, e.getValue().fechar(0), valores));
        }

        return new RelatorioMensalResponse(anoRef, mes, inicio, fim, periodoLabel(anoRef, mes),
                turmas.stream().map(c -> new TurmaSelecionada(c.getId(), c.getNome())).toList(),
                geral.fechar(alunosTotal), linhas, serie);
    }

    /** Turmas do relatório: as escolhidas (validando o escopo) ou todas as permitidas. */
    private List<Classe> resolverTurmas(List<Long> classeIds) {
        List<Classe> ativas = classeRepository.listarAtivas();
        if (classeIds != null && !classeIds.isEmpty()) {
            Set<Long> pedidas = new LinkedHashSet<>(classeIds);
            pedidas.forEach(escopo::assertClasse); // professor só gera das turmas dele
            return ativas.stream().filter(c -> pedidas.contains(c.getId())).toList();
        }
        Set<Long> permitidas = escopo.classesPermitidas(); // null = todas (ADMIN)
        return permitidas == null ? ativas : ativas.stream().filter(c -> permitidas.contains(c.getId())).toList();
    }

    /** Por aula: [presencas, faltas, justificadas, biblias, revistas, licoes]. */
    private Map<Long, long[]> agregadosDeChamada(LocalDate inicio, LocalDate fim, Collection<Long> ids) {
        Map<Long, long[]> mapa = new LinkedHashMap<>();
        for (Object[] r : em.createQuery(
                        "select p.aula.id, "
                                + "sum(case when p.presente = true then 1 else 0 end), "
                                + "sum(case when p.presente = false then 1 else 0 end), "
                                + "sum(case when p.presente = false and p.justificada = true then 1 else 0 end), "
                                + "sum(case when p.trouxeBiblia = true then 1 else 0 end), "
                                + "sum(case when p.trouxeRevista = true then 1 else 0 end), "
                                + "sum(case when p.estudouLicao = true then 1 else 0 end) "
                                + "from Presenca p where p.aula.adiada = false "
                                + "and p.aula.data between :ini and :fim and p.aula.classe.id in :cids "
                                + "group by p.aula.id", Object[].class)
                .setParameter("ini", inicio).setParameter("fim", fim).setParameter("cids", ids)
                .getResultList()) {
            mapa.put(num(r[0]), new long[]{num(r[1]), num(r[2]), num(r[3]), num(r[4]), num(r[5]), num(r[6])});
        }
        return mapa;
    }

    private Map<Long, Long> visitantesPorAula(LocalDate inicio, LocalDate fim, Collection<Long> ids) {
        Map<Long, Long> mapa = new LinkedHashMap<>();
        for (Object[] r : em.createQuery(
                        "select v.aula.id, count(v) from Visitante v where v.aula.adiada = false "
                                + "and v.aula.data between :ini and :fim and v.aula.classe.id in :cids "
                                + "group by v.aula.id", Object[].class)
                .setParameter("ini", inicio).setParameter("fim", fim).setParameter("cids", ids)
                .getResultList()) {
            mapa.put(num(r[0]), num(r[1]));
        }
        return mapa;
    }

    private Map<Long, Long> alunosAtivosPorTurma(Collection<Long> ids) {
        Map<Long, Long> mapa = new LinkedHashMap<>();
        for (Object[] r : em.createQuery(
                        "select al.classe.id, count(al) from Aluno al where al.ativo = true "
                                + "and al.classe.id in :cids group by al.classe.id", Object[].class)
                .setParameter("cids", ids)
                .getResultList()) {
            mapa.put(num(r[0]), num(r[1]));
        }
        return mapa;
    }

    private RelatorioMensalResponse vazio(int ano, Integer mes, LocalDate inicio, LocalDate fim) {
        return new RelatorioMensalResponse(ano, mes, inicio, fim, periodoLabel(ano, mes),
                List.of(), new Totais(0, 0, 0, 0, 0, 0, 0.0, 0, 0, 0, 0), List.of(), List.of());
    }

    private static String periodoLabel(int ano, Integer mes) {
        return mes != null ? MESES[mes - 1] + " de " + ano : "Ano de " + ano;
    }

    /** Rótulo do eixo do gráfico: "03/08" no mês, "Ago" no ano. */
    private static String rotulo(LocalDate data, boolean porDomingo) {
        if (porDomingo) {
            return String.format("%02d/%02d", data.getDayOfMonth(), data.getMonthValue());
        }
        return MESES_CURTOS[data.getMonthValue() - 1];
    }

    /** Presenças ÷ (presenças + faltas), em % com 2 casas. 0 quando não houve chamada. */
    private static double percentual(long presencas, long faltas) {
        long base = presencas + faltas;
        return base > 0 ? Math.round(presencas * 10000.0 / base) / 100.0 : 0.0;
    }

    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
