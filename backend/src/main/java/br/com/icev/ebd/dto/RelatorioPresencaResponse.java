package br.com.icev.ebd.dto;

import java.time.LocalDate;
import java.util.List;

/** Relatório de presenças: período, total de aulas e linha por aluno. */
public record RelatorioPresencaResponse(
        LocalDate inicio,
        LocalDate fim,
        long totalAulas,
        List<RelatorioPresencaItem> itens) {
}
