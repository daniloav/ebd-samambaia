package br.com.ice.ebd.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Boletim de um aluno num trimestre: provas, frequência e situação. */
public record BoletimResponse(
        Long alunoId,
        String alunoNome,
        String turma,
        int ano,
        int trimestre,
        LocalDate periodoInicio,
        LocalDate periodoFim,
        List<ProvaItem> provas,
        BigDecimal mediaNotas,
        double aproveitamentoPct,
        Frequencia frequencia,
        long visitantesTrazidos,
        String situacao) {

    /** Uma prova do período e a nota do aluno (nota/percentual nulos se não lançada). */
    public record ProvaItem(
            String titulo,
            LocalDate data,
            BigDecimal nota,
            BigDecimal notaMaxima,
            Double percentual) {}

    public record Frequencia(
            long totalAulas,
            long presencas,
            long faltas,
            double percentualPresenca,
            long biblias,
            long revistas,
            long licoes) {}
}
