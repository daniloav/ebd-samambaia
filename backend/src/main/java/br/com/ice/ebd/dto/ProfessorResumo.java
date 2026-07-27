package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Usuario;

/** Professor (usuário PROFESSOR) para o seletor "Professor da aula": id do usuário + nome + aluno vinculado. */
public record ProfessorResumo(Long id, String nome, Long alunoId) {

    public static ProfessorResumo de(Usuario u) {
        String nome = u.getAluno() != null ? u.getAluno().getNome() : u.getUsername();
        Long alunoId = u.getAluno() != null ? u.getAluno().getId() : null;
        return new ProfessorResumo(u.getId(), nome, alunoId);
    }
}
