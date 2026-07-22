package br.com.ice.ebd.dto;

import java.math.BigDecimal;
import java.util.List;

/** Estruturas do quiz para montar/editar (professor) e responder (aluno). */
public final class QuizDto {

    private QuizDto() {}

    /** Payload para salvar todas as questões de uma prova. */
    public record Salvar(List<QuestaoIn> questoes) {}

    public record QuestaoIn(String enunciado, String tipo, BigDecimal pontos, List<AlternativaIn> alternativas) {}

    public record AlternativaIn(String texto, boolean correta) {}

    /** Questão como o professor vê (com o gabarito). */
    public record QuestaoEdit(Long id, String enunciado, String tipo, BigDecimal pontos,
                              List<AlternativaEdit> alternativas) {}

    public record AlternativaEdit(Long id, String texto, boolean correta) {}
}
