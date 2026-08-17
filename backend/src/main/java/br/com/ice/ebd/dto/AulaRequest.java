package br.com.ice.ebd.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record AulaRequest(
        @NotNull(message = "A classe é obrigatória")
        Long classeId,
        @NotNull(message = "A data da aula é obrigatória")
        LocalDate data,
        @Size(max = 200, message = "O tema deve ter no máximo 200 caracteres")
        String tema,
        /** Professor (usuário) que deu a aula (opcional). */
        Long professorId,
        /**
         * Leituras bíblicas diárias da lição (opcional). Quando informada, a lista é a
         * <b>íntegra</b> do cadastro: dias ausentes ou com referência em branco são removidos.
         * Nula = não mexe nas leituras já cadastradas.
         */
        @Valid
        List<TextoBiblicoRequest> textos) {
}
