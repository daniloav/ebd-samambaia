package br.com.ice.ebd.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProvaRequest(
        @NotNull(message = "A classe é obrigatória")
        Long classeId,
        @NotBlank(message = "O título é obrigatório")
        @Size(max = 200, message = "O título deve ter no máximo 200 caracteres")
        String titulo,
        @NotNull(message = "A data é obrigatória")
        LocalDate data,
        @NotNull(message = "A nota máxima é obrigatória")
        @DecimalMin(value = "0.01", message = "A nota máxima deve ser maior que zero")
        BigDecimal notaMaxima,
        /** OFFLINE (padrão) ou ONLINE (quiz). Nulo = OFFLINE. */
        String tipo,
        /** Janela da prova online (opcional). */
        LocalDateTime abreEm,
        LocalDateTime fechaEm) {
}
