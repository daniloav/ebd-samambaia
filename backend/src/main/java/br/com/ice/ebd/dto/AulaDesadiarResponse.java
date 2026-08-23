package br.com.ice.ebd.dto;

import java.time.LocalDate;

/**
 * Resultado de retirar o adiamento de uma aula: a aula que voltou a valer, se a aula de
 * <b>reposição</b> criada pelo adiamento foi removida (com a data dela) e quantas aulas da
 * agenda voltaram -7 dias. Quando a reposição precisa ser mantida (já tem chamada lançada, não
 * foi identificada, ou a agenda não pode voltar sem colidir), {@code observacao} explica o
 * porquê — o adiamento é desfeito assim mesmo, só sem mexer na agenda.
 */
public record AulaDesadiarResponse(
        AulaResponse aula,
        boolean reposicaoRemovida,
        LocalDate dataReposicao,
        int aulasMovidas,
        String observacao) {
}
