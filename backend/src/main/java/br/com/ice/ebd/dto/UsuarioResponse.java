package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Usuario;
import java.util.Comparator;
import java.util.List;

public record UsuarioResponse(
        Long id,
        String username,
        boolean ehAdmin,
        boolean ehProfessor,
        boolean ehAluno,
        boolean ativo,
        Long alunoId,
        String alunoNome,
        String email,
        boolean ehTesoureiro,
        boolean ehLider,
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
                u.getId(), u.getUsername(),
                u.isEhAdmin(), u.isEhProfessor(), u.isEhAluno(), u.isAtivo(),
                u.getAluno() != null ? u.getAluno().getId() : null,
                u.getAluno() != null ? u.getAluno().getNome() : null,
                u.getEmail(),
                u.isEhTesoureiro(),
                u.isEhLider(),
                cs);
    }
}
