package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.DiaSemanaLeitura;
import br.com.ice.ebd.model.TextoBiblicoAula;
import java.time.LocalDate;

public record TextoBiblicoResponse(
        Long id,
        DiaSemanaLeitura diaSemana,
        String diaSemanaRotulo,
        /** Data em que a leitura é enviada: o dia da semana anterior à aula. */
        LocalDate dataLeitura,
        String referencia,
        LocalDate enviadoEm) {

    public static TextoBiblicoResponse de(TextoBiblicoAula t) {
        return new TextoBiblicoResponse(t.getId(), t.getDiaSemana(), t.getDiaSemana().getRotulo(),
                t.dataDaLeitura(), t.getReferencia(), t.getEnviadoEm());
    }
}
