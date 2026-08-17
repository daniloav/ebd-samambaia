package br.com.ice.ebd.model;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Dia da semana de uma leitura bíblica diária da lição. A semana começa na <b>segunda</b> e
 * termina no <b>domingo</b> — a mesma ordem em que as leituras aparecem na revista.
 *
 * <p>As leituras são de <b>preparação</b>: pertencem à semana que <b>termina no dia da aula</b>.
 * A data de uma leitura é a ocorrência daquele dia da semana na janela dos 7 dias que fecha na
 * aula. Para a aula de domingo 23/08, {@code SEGUNDA} cai em 17/08, {@code SABADO} em 22/08 e
 * {@code DOMINGO} no próprio dia da aula (23/08, com o e-mail saindo de manhã, antes da classe).
 */
public enum DiaSemanaLeitura {

    SEGUNDA(DayOfWeek.MONDAY, "Segunda-feira"),
    TERCA(DayOfWeek.TUESDAY, "Terça-feira"),
    QUARTA(DayOfWeek.WEDNESDAY, "Quarta-feira"),
    QUINTA(DayOfWeek.THURSDAY, "Quinta-feira"),
    SEXTA(DayOfWeek.FRIDAY, "Sexta-feira"),
    SABADO(DayOfWeek.SATURDAY, "Sábado"),
    DOMINGO(DayOfWeek.SUNDAY, "Domingo");

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
     * Data em que esta leitura deve ser lida (e o e-mail enviado), dada a data da aula: a
     * ocorrência deste dia da semana na janela de 7 dias que <b>termina na própria aula</b> —
     * sempre entre {@code dataAula - 6} e {@code dataAula}. Numa aula de domingo, a leitura de
     * domingo é a do dia da aula; as demais caem na semana que a antecede.
     */
    public LocalDate dataParaAula(LocalDate dataAula) {
        LocalDate d = dataAula;
        while (d.getDayOfWeek() != dia) {
            d = d.minusDays(1);
        }
        return d;
    }
}
