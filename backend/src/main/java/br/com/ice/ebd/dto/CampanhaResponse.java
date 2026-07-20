package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Campanha;
import java.time.LocalDateTime;

public record CampanhaResponse(
        Long id,
        String titulo,
        String mensagem,
        Long classeId,
        String classeNome,
        int totalEnviados,
        String criadoPor,
        LocalDateTime dataEnvio) {

    public static CampanhaResponse de(Campanha c) {
        return new CampanhaResponse(
                c.getId(),
                c.getTitulo(),
                c.getMensagem(),
                c.getClasse() != null ? c.getClasse().getId() : null,
                c.getClasse() != null ? c.getClasse().getNome() : "Todas as turmas",
                c.getTotalEnviados(),
                c.getCriadoPor(),
                c.getDataEnvio());
    }
}
