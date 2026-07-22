package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Prova;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProvaResponse(
        Long id,
        String titulo,
        LocalDate data,
        BigDecimal notaMaxima,
        Long classeId,
        String classeNome,
        String tipo,
        LocalDateTime abreEm,
        LocalDateTime fechaEm,
        long numQuestoes) {

    public static ProvaResponse de(Prova p) {
        return de(p, 0);
    }

    public static ProvaResponse de(Prova p, long numQuestoes) {
        return new ProvaResponse(p.getId(), p.getTitulo(), p.getData(), p.getNotaMaxima(),
                p.getClasse().getId(), p.getClasse().getNome(),
                p.getTipo() != null ? p.getTipo().name() : "OFFLINE",
                p.getAbreEm(), p.getFechaEm(), numQuestoes);
    }
}
