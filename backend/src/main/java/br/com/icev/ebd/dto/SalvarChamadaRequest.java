package br.com.icev.ebd.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Payload para salvar a chamada de uma aula (uma linha por aluno). */
public record SalvarChamadaRequest(
        @NotNull(message = "Informe os registros da chamada")
        List<Item> itens) {

    public record Item(
            @NotNull(message = "alunoId é obrigatório") Long alunoId,
            boolean presente,
            boolean trouxeBiblia,
            boolean trouxeRevista,
            boolean estudouLicao) {
    }
}
