package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Auditoria;
import java.time.LocalDateTime;

public record AuditoriaResponse(
        Long id,
        LocalDateTime dataHora,
        String usuario,
        String acao,
        String entidade,
        Long entidadeId,
        String descricao) {

    public static AuditoriaResponse de(Auditoria a) {
        return new AuditoriaResponse(a.getId(), a.getDataHora(), a.getUsuario(),
                a.getAcao().name(), a.getEntidade().name(), a.getEntidadeId(), a.getDescricao());
    }
}
