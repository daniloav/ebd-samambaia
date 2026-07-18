package br.com.icev.ebd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AlunoRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String nome,
        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres")
        String telefone,
        LocalDate dataNascimento,
        Boolean ativo) {
}
