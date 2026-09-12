package br.com.ice.ebd.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs da visão do aluno para provas respondidas pela tela (ONLINE e RECUPERACAO). */
public final class QuizAlunoDto {

    private QuizAlunoDto() {}

    /** Status da prova para o aluno. */
    public enum Status { FUTURA, DISPONIVEL, RESPONDIDA, FECHADA }

    /**
     * Item da lista "Minhas provas". {@code nota} é a melhor nota obtida (null se nunca respondeu).
     * Na RECUPERACAO, {@code presencaEquivalente} é quanto dessa nota vira presença (0 a 1) e a
     * prova segue DISPONIVEL enquanto houver tentativa e a nota máxima não tiver sido atingida.
     */
    public record ProvaResumo(
            Long id, String titulo, LocalDate data, BigDecimal notaMaxima,
            long numQuestoes, String status, LocalDateTime abreEm, LocalDateTime fechaEm,
            BigDecimal nota, String tipo, int tentativasUsadas, int tentativasMax,
            LocalDate aulaData, String aulaTema, BigDecimal presencaEquivalente) {
    }

    /** Alternativa exibida ao aluno — SEM o gabarito. */
    public record AlternativaResponder(Long id, String texto) {}

    public record QuestaoResponder(
            Long id, String enunciado, String tipo, BigDecimal pontos,
            List<AlternativaResponder> alternativas) {
    }

    /** O quiz para responder (sem indicar as corretas). {@code tentativa} = a que será enviada. */
    public record ParaResponder(
            Long provaId, String titulo, BigDecimal notaMaxima, List<QuestaoResponder> questoes,
            String tipo, int tentativa, int tentativasMax, LocalDate aulaData, String aulaTema) {
    }

    /** Envio do aluno: a alternativa escolhida por questão. */
    public record SubmeterRequest(List<RespostaIn> respostas) {}

    public record RespostaIn(Long questaoId, Long alternativaId) {}

    /** Correção de uma questão (com o gabarito, para estudo). */
    public record ResultadoQuestao(
            Long questaoId, String enunciado, Long escolhidaId, Long corretaId,
            boolean acertou, BigDecimal pontos, List<AlternativaResponder> alternativas) {
    }

    /**
     * Resultado de uma tentativa auto-corrigida. {@code melhorNota}, {@code presencaEquivalente}
     * e {@code podeTentarDeNovo} consideram todas as tentativas do aluno na prova.
     */
    public record Resultado(
            String titulo, BigDecimal nota, BigDecimal notaMaxima, int acertos, int total,
            List<ResultadoQuestao> questoes,
            String tipo, int tentativa, int tentativasMax, BigDecimal melhorNota,
            BigDecimal presencaEquivalente, boolean podeTentarDeNovo) {
    }
}
