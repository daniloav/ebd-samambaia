package br.com.icev.ebd.dto;

import br.com.icev.ebd.model.Aluno;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AlunoResponse(
        Long id,
        String nome,
        String telefone,
        LocalDate dataNascimento,
        boolean ativo,
        LocalDateTime dataCadastro) {

    public static AlunoResponse de(Aluno a) {
        return new AlunoResponse(a.getId(), a.getNome(), a.getTelefone(),
                a.getDataNascimento(), a.isAtivo(), a.getDataCadastro());
    }
}
