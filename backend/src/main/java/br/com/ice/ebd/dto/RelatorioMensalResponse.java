package br.com.ice.ebd.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Relatório geral de presença de um mês (ou do ano inteiro), consolidando as turmas
 * escolhidas — uma, várias ou todas as que o usuário pode ver.
 *
 * <p>Traz três visões dos mesmos dados: os {@link Totais} do período, a quebra
 * {@link LinhaTurma por turma} e a {@link PontoSerie série temporal} que alimenta o gráfico
 * (um ponto por <b>domingo</b> quando o filtro é um mês; um ponto por <b>mês</b> quando é o ano).
 */
public record RelatorioMensalResponse(
        int ano,
        /** Mês do filtro (1–12), ou {@code null} quando o relatório é do ano inteiro. */
        Integer mes,
        LocalDate inicio,
        LocalDate fim,
        /** Rótulo pronto para a tela/exportação: "Agosto de 2026" ou "Ano de 2026". */
        String periodoLabel,
        /** Turmas efetivamente consideradas (as escolhidas, ou todas as permitidas). */
        List<TurmaSelecionada> turmas,
        Totais totais,
        List<LinhaTurma> porTurma,
        List<PontoSerie> serie) {

    /** Turma incluída no relatório. */
    public record TurmaSelecionada(Long classeId, String classeNome) {
    }

    /**
     * Números agregados de um recorte (o período todo, uma turma ou um ponto da série).
     *
     * @param aulas          aulas válidas (não adiadas) no recorte
     * @param aulasComChamada aulas que já tiveram a chamada registrada
     * @param alunosAtivos   alunos ativos das turmas do recorte (0 na série)
     * @param presencas      registros de presença (presente = true)
     * @param faltas         registros de falta (presente = false)
     * @param faltasJustificadas subconjunto das faltas com justificativa do professor
     * @param percentualPresenca presenças ÷ (presenças + faltas), em %
     */
    public record Totais(
            long aulas,
            long aulasComChamada,
            long alunosAtivos,
            long presencas,
            long faltas,
            long faltasJustificadas,
            double percentualPresenca,
            long biblias,
            long revistas,
            long licoes,
            long visitantes) {
    }

    /** Uma turma no período. */
    public record LinhaTurma(Long classeId, String classeNome, Totais totais) {
    }

    /**
     * Um ponto do gráfico: o consolidado da data/mês e o valor de cada turma
     * (para as barras agrupadas quando há mais de uma turma).
     */
    public record PontoSerie(
            /** Rótulo curto do eixo: "03/08" (domingo) ou "Ago" (mês). */
            String rotulo,
            /** Data do ponto: a data da aula, ou o 1º dia do mês na visão anual. */
            LocalDate data,
            Totais totais,
            List<ValorTurma> porTurma) {
    }

    /** Valor de uma turma dentro de um ponto da série. */
    public record ValorTurma(
            Long classeId,
            String classeNome,
            long presencas,
            long faltas,
            double percentualPresenca) {
    }
}
