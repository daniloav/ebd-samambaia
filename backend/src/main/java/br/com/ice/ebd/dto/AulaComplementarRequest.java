package br.com.ice.ebd.dto;

import jakarta.validation.constraints.Size;

/**
 * Pedido para desdobrar uma aula: cria a continuação no próximo domingo (origem + 7 dias)
 * e empurra a agenda seguinte da turma +1 domingo. Ambos os campos são opcionais — quando
 * ausentes, herdam o tema (com sufixo "(continuação)") e o professor da aula de origem.
 */
public record AulaComplementarRequest(
        @Size(max = 200, message = "O tema deve ter no máximo 200 caracteres")
        String tema,
        /** Professor (usuário) da aula complementar (opcional; padrão = o da aula de origem). */
        Long professorId) {
}
