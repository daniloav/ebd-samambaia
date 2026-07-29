package br.com.ice.ebd.dto;

/**
 * Resultado do desdobramento: a aula complementar criada e quantas aulas da agenda
 * foram empurradas +7 dias para abrir espaço.
 */
public record AulaComplementarResponse(
        AulaResponse aula,
        int aulasMovidas) {
}
