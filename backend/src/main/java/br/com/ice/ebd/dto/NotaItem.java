package br.com.ice.ebd.dto;

import java.math.BigDecimal;

/** Nota de um aluno em uma prova (null quando ainda não lançada). */
public record NotaItem(
        Long alunoId,
        String alunoNome,
        BigDecimal nota) {
}
