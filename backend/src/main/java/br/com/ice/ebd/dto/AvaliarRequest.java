package br.com.ice.ebd.dto;

import java.math.BigDecimal;

/** Avaliação do tesoureiro: valorAprovado (só na aprovação) + parecer. */
public record AvaliarRequest(BigDecimal valorAprovado, String parecer) {
}
