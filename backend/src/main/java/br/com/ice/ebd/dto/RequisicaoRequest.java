package br.com.ice.ebd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Abertura de uma requisição pelo líder. */
public record RequisicaoRequest(
        @NotBlank(message = "Informe o ministério")
        @Size(max = 120) String ministerio,
        @Size(max = 160) String nomeEvento,
        @NotBlank(message = "Informe a destinação do recurso")
        @Size(max = 300) String destinacao,
        @NotBlank(message = "Informe o motivo") String motivo,
        @NotNull(message = "Informe o valor solicitado")
        @Positive(message = "O valor deve ser maior que zero") BigDecimal valorSolicitado,
        LocalDate dataNecessidade,
        /** DINHEIRO | PIX (default DINHEIRO). */
        String formaRepasse,
        /** CPF | EMAIL | TELEFONE (só quando PIX; nunca aleatória). */
        String pixTipo,
        String pixChave) {
}
