package br.com.ice.ebd.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Regras da prova de RECUPERACAO num lugar só — usadas na tela do aluno
 * ({@link QuizAlunoService}) e na pontuação dos rankings ({@link DesafiosService}).
 *
 * <p>A recuperação é de uma aula e vale até <b>1 presença</b>: nota máxima = 1, abaixo disso o
 * proporcional. Todos da turma podem fazer. O bônus soma ao que a chamada já deu à aula, com
 * uma exceção: a falta justificada (0,3) não acumula — vale o maior entre 0,3 e o proporcional.
 */
public final class Recuperacao {

    /** Tentativas por aluno; vale a melhor nota. */
    public static final int TENTATIVAS = 3;

    private static final double PESO_FALTA_JUSTIFICADA = 0.3;

    private Recuperacao() {}

    /** Fração de uma presença que a nota vale: nota ÷ nota máxima, entre 0 e 1, com 2 casas. */
    public static BigDecimal fracaoPresenca(BigDecimal nota, BigDecimal notaMaxima) {
        if (nota == null || notaMaxima == null || notaMaxima.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal f = nota.divide(notaMaxima, 4, RoundingMode.HALF_UP);
        if (f.compareTo(BigDecimal.ONE) > 0) {
            f = BigDecimal.ONE;
        } else if (f.signum() < 0) {
            f = BigDecimal.ZERO;
        }
        return f.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Pontos de presença que a recuperação acrescenta aos da chamada daquela aula.
     *
     * @param presente    {@code null} quando o aluno não tem registro na chamada (conta como falta)
     * @param justificada falta justificada na chamada (já vale 0,3)
     */
    public static double bonus(double fracao, Boolean presente, Boolean justificada) {
        if (Boolean.FALSE.equals(presente) && Boolean.TRUE.equals(justificada)) {
            return Math.max(0.0, fracao - PESO_FALTA_JUSTIFICADA);
        }
        return fracao;
    }
}
