package br.com.ice.ebd.service;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.time.LocalDate;

/**
 * Intervalo de datas de um recorte (trimestre ou período aberto). Trimestres:
 * 1=Jan-Mar, 2=Abr-Jun, 3=Jul-Set, 4=Out-Dez.
 */
public record PeriodoLetivo(LocalDate inicio, LocalDate fim) {

    /** Datas de um trimestre do ano. */
    public static PeriodoLetivo trimestre(int ano, int trimestre) {
        if (trimestre < 1 || trimestre > 4) {
            throw new WebApplicationException("Trimestre deve ser 1, 2, 3 ou 4.", Response.Status.BAD_REQUEST);
        }
        LocalDate ini = LocalDate.of(ano, (trimestre - 1) * 3 + 1, 1);
        return new PeriodoLetivo(ini, ini.plusMonths(3).minusDays(1));
    }

    /**
     * Se {@code ano} e {@code trimestre} vierem, recorta o trimestre; senão, período
     * aberto (tudo até hoje) — preserva o comportamento sem filtro.
     */
    public static PeriodoLetivo deOuTudo(Integer ano, Integer trimestre) {
        if (ano != null && trimestre != null) {
            return trimestre(ano, trimestre);
        }
        return new PeriodoLetivo(LocalDate.of(2000, 1, 1), LocalDate.now());
    }
}
