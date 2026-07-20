package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Usuario;
import java.util.Comparator;
import java.util.List;

public record UsuarioResponse(
        Long id,
        String username,
        String role,
        boolean ativo,
        Long alunoId,
        String alunoNome,
        String email,
        List<ClasseResumo> classes) {

    /** Turma vinculada ao professor (só id + nome, para a UI). */
    public record ClasseResumo(Long id, String nome) {
    }

    public static UsuarioResponse de(Usuario u) {
        List<ClasseResumo> cs = u.getClasses().stream()
                .map(c -> new ClasseResumo(c.getId(), c.getNome()))
                .sorted(Comparator.comparing(ClasseResumo::nome))
                .toList();
        return new UsuarioResponse(
                u.getId(), u.getUsername(), u.getRole().name(), u.isAtivo(),
                u.getAluno() != null ? u.getAluno().getId() : null,
                u.getAluno() != null ? u.getAluno().getNome() : null,
                u.getEmail(),
                cs);
    }
}
