package br.com.ice.ebd.dto;

import java.time.LocalDate;
import java.util.List;

/** Números e séries agregadas do painel (por turma selecionada, ou todas). */
public record DashboardResponse(
        long totalAlunos,
        long totalAulas,
        long totalProvas,
        double presencaMediaPct,
        List<PontoFrequencia> frequenciaPorAula,
        Distribuicao distribuicao) {

    /** Presença de uma aula (para o gráfico de frequência ao longo do tempo). */
    public record PontoFrequencia(LocalDate data, String tema, long presentes, long total, double pct) {}

    /** Distribuição dos alunos por faixa de frequência. */
    public record Distribuicao(long excelente, long boa, long atencao) {} // >=90 / 75-89 / <75
}
