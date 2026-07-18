package br.com.icev.ebd.dto;

import br.com.icev.ebd.model.Prova;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProvaResponse(
        Long id,
        String titulo,
        LocalDate data,
        BigDecimal notaMaxima) {

    public static ProvaResponse de(Prova p) {
        return new ProvaResponse(p.getId(), p.getTitulo(), p.getData(), p.getNotaMaxima());
    }
}
