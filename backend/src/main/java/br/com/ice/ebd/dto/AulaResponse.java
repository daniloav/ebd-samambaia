package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Aula;
import java.time.LocalDate;

public record AulaResponse(
        Long id,
        LocalDate data,
        String tema,
        Long classeId,
        String classeNome) {

    public static AulaResponse de(Aula a) {
        return new AulaResponse(a.getId(), a.getData(), a.getTema(),
                a.getClasse().getId(), a.getClasse().getNome());
    }
}
