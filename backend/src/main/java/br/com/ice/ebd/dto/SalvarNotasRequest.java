package br.com.ice.ebd.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** Payload para lançar/atualizar as notas de uma prova. */
public record SalvarNotasRequest(
        @NotNull(message = "Informe as notas") List<Item> itens) {

    public record Item(
            @NotNull(message = "alunoId é obrigatório") Long alunoId,
            @DecimalMin(value = "0.0", message = "A nota não pode ser negativa") BigDecimal nota) {
    }
}
