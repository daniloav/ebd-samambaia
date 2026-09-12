package br.com.ice.ebd.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Junção de requisições: os ids das requisições em aberto que serão absorvidas pela principal
 * (a do caminho da URL), cujo valor solicitado passa a ser a soma de todas.
 */
public record JuntarRequisicoesRequest(
        @NotEmpty(message = "Selecione ao menos uma requisição para juntar") List<Long> ids) {
}
