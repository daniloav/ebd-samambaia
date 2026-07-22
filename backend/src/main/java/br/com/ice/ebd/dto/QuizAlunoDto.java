package br.com.ice.ebd.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs da visão do aluno para provas ONLINE (listar, responder, submeter, resultado). */
public final class QuizAlunoDto {

    private QuizAlunoDto() {}

    /** Status da prova online para o aluno. */
    public enum Status { FUTURA, DISPONIVEL, RESPONDIDA, FECHADA }

    /** Item da lista "Minhas provas". {@code nota} preenchida só quando RESPONDIDA. */
    public record ProvaResumo(
            Long id, String titulo, LocalDate data, BigDecimal notaMaxima,
            long numQuestoes, String status, LocalDateTime abreEm, LocalDateTime fechaEm,
            BigDecimal nota) {
    }

    /** Alternativa exibida ao aluno — SEM o gabarito. */
    public record AlternativaResponder(Long id, String texto) {}

    public record QuestaoResponder(
            Long id, String enunciado, String tipo, BigDecimal pontos,
            List<AlternativaResponder> alternativas) {
    }

    /** O quiz para responder (sem indicar as corretas). */
    public record ParaResponder(
            Long provaId, String titulo, BigDecimal notaMaxima, List<QuestaoResponder> questoes) {
    }

    /** Envio do aluno: a alternativa escolhida por questão. */
    public record SubmeterRequest(List<RespostaIn> respostas) {}

    public record RespostaIn(Long questaoId, Long alternativaId) {}

    /** Correção de uma questão (com o gabarito, para estudo). */
    public record ResultadoQuestao(
            Long questaoId, String enunciado, Long escolhidaId, Long corretaId,
            boolean acertou, BigDecimal pontos, List<AlternativaResponder> alternativas) {
    }

    /** Resultado da submissão auto-corrigida. */
    public record Resultado(
            String titulo, BigDecimal nota, BigDecimal notaMaxima, int acertos, int total,
            List<ResultadoQuestao> questoes) {
    }
}
