package br.com.ice.ebd.dto;

import java.time.LocalDate;
import java.util.List;

/** Relatório geral consolidado de um dia (todas as turmas), para a superintendência. */
public record RelatorioGeralResponse(
        LocalDate data,
        int totalTurmas,
        Totais totais,
        List<LinhaTurma> turmas) {

    /** Totais somados de uma turma no dia. */
    public record LinhaTurma(
            Long classeId,
            String classeNome,
            String tema,
            long presentes,
            long faltosos,
            long biblias,
            long revistas,
            long licoes,
            long visitantes) {
    }

    /** Grande total do dia (soma de todas as turmas). */
    public record Totais(
            long presentes,
            long faltosos,
            long biblias,
            long revistas,
            long licoes,
            long visitantes) {
    }
}
