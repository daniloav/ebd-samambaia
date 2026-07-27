package br.com.ice.ebd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** senha é obrigatória na criação; no update, se vier em branco, mantém a atual. */
public record UsuarioRequest(
        @NotBlank(message = "O usuário é obrigatório")
        @Size(max = 60, message = "O usuário deve ter no máximo 60 caracteres")
        String username,
        String senha,
        Boolean ehAdmin,
        Boolean ehProfessor,
        Boolean ehAluno,
        Long alunoId,
        /** Turmas vinculadas ao professor (RBAC). Ignorado para ADMIN/ALUNO. */
        List<Long> classeIds,
        String email,
        Boolean ativo,
        /** Capacidades funcionais (somam-se à role base; ADMIN já tem tudo). */
        Boolean ehTesoureiro,
        Boolean ehLider) {
}
