package br.com.ice.ebd.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
            boolean estudouLicao,
            /** Falta justificada pelo professor (ignorado quando presente). */
            boolean justificada,
            @Size(max = 300, message = "O motivo deve ter no máximo 300 caracteres")
            String justificativaMotivo) {

        /** Atalho sem justificativa (usado onde a justificativa não se aplica). */
        public Item(Long alunoId, boolean presente, boolean trouxeBiblia,
                    boolean trouxeRevista, boolean estudouLicao) {
            this(alunoId, presente, trouxeBiblia, trouxeRevista, estudouLicao, false, null);
        }
    }
}
