package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Usuario;

public record UsuarioResponse(
        Long id,
        String username,
        String role,
        boolean ativo,
        Long alunoId,
        String alunoNome) {

    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(
                u.getId(), u.getUsername(), u.getRole().name(), u.isAtivo(),
                u.getAluno() != null ? u.getAluno().getId() : null,
                u.getAluno() != null ? u.getAluno().getNome() : null);
    }
}
