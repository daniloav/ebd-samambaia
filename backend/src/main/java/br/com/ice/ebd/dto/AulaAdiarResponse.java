package br.com.ice.ebd.dto;

/**
 * Resultado de adiar uma aula: a aula que foi marcada como adiada (pontuação desabilitada),
 * a aula de reposição criada no domingo seguinte e quantas aulas da agenda foram
 * empurradas +7 dias para abrir espaço.
 */
public record AulaAdiarResponse(
        AulaResponse aulaAdiada,
        AulaResponse reposicao,
        int aulasMovidas) {
}
