package br.com.ice.ebd.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AulaRequest(
        @NotNull(message = "A classe é obrigatória")
        Long classeId,
        @NotNull(message = "A data da aula é obrigatória")
        LocalDate data,
        @Size(max = 200, message = "O tema deve ter no máximo 200 caracteres")
        String tema) {
}
