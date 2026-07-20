package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** senha é obrigatória na criação; no update, se vier em branco, mantém a atual. */
public record UsuarioRequest(
        @NotBlank(message = "O usuário é obrigatório")
        @Size(max = 60, message = "O usuário deve ter no máximo 60 caracteres")
        String username,
        String senha,
        @NotNull(message = "O perfil é obrigatório")
        Role role,
        Long alunoId,
        /** Turmas vinculadas ao professor (RBAC). Ignorado para ADMIN/ALUNO. */
        List<Long> classeIds,
        String email,
        Boolean ativo) {
}
