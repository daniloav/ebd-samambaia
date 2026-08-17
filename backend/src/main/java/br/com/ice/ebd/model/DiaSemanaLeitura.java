package br.com.ice.ebd.model;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Dia da semana de uma leitura bíblica diária da lição.
 *
 * <p>As leituras pertencem à <b>semana anterior</b> ao dia da aula (preparação, como nas
 * revistas): a data de uma leitura é a <b>ocorrência mais recente daquele dia da semana antes
 * da aula</b>. Para a aula de domingo 23/08, {@code SEGUNDA} cai em 17/08 e {@code SABADO} em
 * 22/08; {@code DOMINGO} cai no domingo anterior (16/08).
 */
public enum DiaSemanaLeitura {

    DOMINGO(DayOfWeek.SUNDAY, "Domingo"),
    SEGUNDA(DayOfWeek.MONDAY, "Segunda-feira"),
    TERCA(DayOfWeek.TUESDAY, "Terça-feira"),
    QUARTA(DayOfWeek.WEDNESDAY, "Quarta-feira"),
    QUINTA(DayOfWeek.THURSDAY, "Quinta-feira"),
    SEXTA(DayOfWeek.FRIDAY, "Sexta-feira"),
    SABADO(DayOfWeek.SATURDAY, "Sábado");

    private final DayOfWeek dia;
    private final String rotulo;

    DiaSemanaLeitura(DayOfWeek dia, String rotulo) {
        this.dia = dia;
        this.rotulo = rotulo;
    }

    public DayOfWeek getDia() {
        return dia;
    }

    public String getRotulo() {
        return rotulo;
    }

    public static DiaSemanaLeitura de(DayOfWeek dia) {
        for (DiaSemanaLeitura d : values()) {
            if (d.dia == dia) {
                return d;
            }
        }
        throw new IllegalArgumentException("Dia da semana inválido: " + dia);
    }

    /**
     * Data em que esta leitura deve ser lida (e o e-mail enviado), dada a data da aula:
     * a última ocorrência deste dia da semana <b>antes</b> da aula — sempre entre
     * {@code dataAula - 7} e {@code dataAula - 1}.
     */
    public LocalDate dataAntesDe(LocalDate dataAula) {
        LocalDate d = dataAula.minusDays(1);
        while (d.getDayOfWeek() != dia) {
            d = d.minusDays(1);
        }
        return d;
    }
}
