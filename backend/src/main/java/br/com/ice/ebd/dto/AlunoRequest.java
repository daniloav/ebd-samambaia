package br.com.ice.ebd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AlunoRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
        String nome,
        @NotNull(message = "A classe é obrigatória")
        Long classeId,
        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres")
        String telefone,
        LocalDate dataNascimento,
        @Email(message = "E-mail inválido")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres")
        String email,
        Boolean recebeNotificacoes,
        Boolean ativo) {
}
