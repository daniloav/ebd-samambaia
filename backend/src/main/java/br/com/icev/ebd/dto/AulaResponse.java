package br.com.icev.ebd.dto;

import br.com.icev.ebd.model.Aula;
import java.time.LocalDate;

public record AulaResponse(
        Long id,
        LocalDate data,
        String tema) {

    public static AulaResponse de(Aula a) {
        return new AulaResponse(a.getId(), a.getData(), a.getTema());
    }
}
