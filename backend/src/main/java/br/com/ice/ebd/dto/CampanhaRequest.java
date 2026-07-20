package br.com.ice.ebd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload para criar e enviar uma campanha. classeId nulo = todas as turmas. */
public record CampanhaRequest(
        @NotBlank(message = "O título é obrigatório")
        @Size(max = 150, message = "O título deve ter no máximo 150 caracteres")
        String titulo,

        @NotBlank(message = "A mensagem é obrigatória")
        @Size(max = 5000, message = "A mensagem deve ter no máximo 5000 caracteres")
        String mensagem,

        Long classeId) {
}
