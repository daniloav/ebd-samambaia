package br.com.ice.ebd.dto;

import java.util.List;

/**
 * Ranking das turmas entre si (desafio sadio entre classes). Cada turma é pontuada pela
 * <b>média de pontos por aluno</b> (total da turma ÷ nº de alunos ativos), para que turmas
 * de tamanhos diferentes compitam de forma justa.
 *
 * <p>{@code minhaClasseId} só é preenchido na visão do aluno (para destacar a turma dele);
 * na visão de gestão/professor vem {@code null}.</p>
 */
public record RankingTurmasResponse(
        long totalAulas,
        long totalProvas,
        Long minhaClasseId,
        List<Item> turmas) {

    /** Uma turma no ranking. {@code valor} = média de pontos por aluno; {@code total} = soma bruta. */
    public record Item(int posicao, Long classeId, String turmaNome,
                       double valor, double total, int alunos, String detalhe) {}
}
