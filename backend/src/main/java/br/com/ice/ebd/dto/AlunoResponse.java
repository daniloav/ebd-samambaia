package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Aluno;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AlunoResponse(
        Long id,
        String nome,
        String telefone,
        LocalDate dataNascimento,
        boolean ativo,
        LocalDateTime dataCadastro,
        Long classeId,
        String classeNome) {

    public static AlunoResponse de(Aluno a) {
        return new AlunoResponse(a.getId(), a.getNome(), a.getTelefone(),
                a.getDataNascimento(), a.isAtivo(), a.getDataCadastro(),
                a.getClasse().getId(), a.getClasse().getNome());
    }
}
