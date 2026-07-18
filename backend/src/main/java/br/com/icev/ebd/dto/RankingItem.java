package br.com.icev.ebd.dto;

/**
 * Uma posição em um ranking de desafio.
 * posicao = colocação (1º, 2º...), valor = métrica principal, detalhe = texto auxiliar.
 */
public record RankingItem(
        int posicao,
        Long alunoId,
        String nome,
        double valor,
        String detalhe) {
}
