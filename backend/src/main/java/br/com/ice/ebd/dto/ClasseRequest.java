package br.com.ice.ebd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClasseRequest(
        @NotBlank(message = "O nome da classe é obrigatório")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String nome,
        @Size(max = 300, message = "A descrição deve ter no máximo 300 caracteres")
        String descricao,
        Boolean ativo) {
}
