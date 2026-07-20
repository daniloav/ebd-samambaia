package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Visitante;
import java.time.LocalDateTime;

public record VisitanteResponse(
        Long id,
        String nome,
        String email,
        String telefone,
        Long trazidoPorId,
        String trazidoPorNome,
        LocalDateTime dataCadastro) {

    public static VisitanteResponse de(Visitante v) {
        return new VisitanteResponse(
                v.getId(), v.getNome(), v.getEmail(), v.getTelefone(),
                v.getTrazidoPor() != null ? v.getTrazidoPor().getId() : null,
                v.getTrazidoPor() != null ? v.getTrazidoPor().getNome() : null,
                v.getDataCadastro());
    }
}
