package br.com.ice.ebd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Justificativa de falta enviada pelo próprio aluno. */
public record JustificarFaltaRequest(
        @NotBlank(message = "Informe o motivo da falta")
        @Size(max = 300, message = "O motivo deve ter no máximo 300 caracteres")
        String motivo) {
}
