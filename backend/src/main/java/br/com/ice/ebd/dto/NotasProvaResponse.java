package br.com.ice.ebd.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Grade de notas de uma prova: dados da prova + uma linha por aluno. */
public record NotasProvaResponse(
        Long provaId,
        String titulo,
        LocalDate data,
        BigDecimal notaMaxima,
        List<NotaItem> itens) {
}
