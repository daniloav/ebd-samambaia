package br.com.ice.ebd.dto;

import java.util.List;

/** Ranking resumido para o aluno: pódio (top 3) da classificação geral + a posição dele. */
public record MeuRankingResponse(
        String turmaNome,
        int totalParticipantes,
        List<Item> podio,
        Item minhaPosicao) {

    public record Item(int posicao, Long alunoId, String nome, double valor, String detalhe, boolean eu) {}
}
