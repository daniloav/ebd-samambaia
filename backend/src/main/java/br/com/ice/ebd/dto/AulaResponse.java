package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.Aula;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record AulaResponse(
        Long id,
        LocalDate data,
        String tema,
        Long classeId,
        String classeNome,
        Long professorId,
        String professorNome,
        Long professorAlunoId,
        boolean adiada,
        /** Leituras bíblicas diárias da lição, de domingo a sábado (vazia se não houver). */
        List<TextoBiblicoResponse> textos) {

    public static AulaResponse de(Aula a) {
        var prof = a.getProfessor();
        Long profId = prof != null ? prof.getId() : null;
        Long profAlunoId = prof != null && prof.getAluno() != null ? prof.getAluno().getId() : null;
        String profNome = prof == null ? null
                : (prof.getAluno() != null ? prof.getAluno().getNome() : prof.getUsername());
        List<TextoBiblicoResponse> textos = a.getTextos().stream()
                .sorted(Comparator.comparing(t -> t.getDiaSemana().ordinal()))
                .map(TextoBiblicoResponse::de)
                .toList();
        return new AulaResponse(a.getId(), a.getData(), a.getTema(),
                a.getClasse().getId(), a.getClasse().getNome(), profId, profNome, profAlunoId,
                a.isAdiada(), textos);
    }
}
