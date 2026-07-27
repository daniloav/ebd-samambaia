package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Aula;
import java.time.LocalDate;

public record AulaResponse(
        Long id,
        LocalDate data,
        String tema,
        Long classeId,
        String classeNome,
        Long professorId,
        String professorNome,
        Long professorAlunoId) {

    public static AulaResponse de(Aula a) {
        var prof = a.getProfessor();
        Long profId = prof != null ? prof.getId() : null;
        Long profAlunoId = prof != null && prof.getAluno() != null ? prof.getAluno().getId() : null;
        String profNome = prof == null ? null
                : (prof.getAluno() != null ? prof.getAluno().getNome() : prof.getUsername());
        return new AulaResponse(a.getId(), a.getData(), a.getTema(),
                a.getClasse().getId(), a.getClasse().getNome(), profId, profNome, profAlunoId);
    }
}
