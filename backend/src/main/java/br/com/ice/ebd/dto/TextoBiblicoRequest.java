package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.DiaSemanaLeitura;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Leitura bíblica de um dia da semana no cadastro da aula. Referência em branco (ou nula)
 * significa "sem leitura nesse dia" — a linha existente é removida.
 */
public record TextoBiblicoRequest(
        @NotNull(message = "O dia da semana da leitura é obrigatório")
        DiaSemanaLeitura diaSemana,
        @Size(max = 200, message = "A referência bíblica deve ter no máximo 200 caracteres")
        String referencia) {
}
