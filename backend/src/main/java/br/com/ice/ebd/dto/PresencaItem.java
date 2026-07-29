package br.com.ice.ebd.dto;

/** Linha da chamada: um aluno e seus itens avaliados em uma aula. */
public record PresencaItem(
        Long alunoId,
        String alunoNome,
        boolean presente,
        boolean trouxeBiblia,
        boolean trouxeRevista,
        boolean estudouLicao,
        /** true = este aluno é o professor desta aula (desabilitado; não conta). */
        boolean professorDaAula,
        /** Falta justificada pelo professor (só faz sentido quando ausente). */
        boolean justificada,
        String justificativaMotivo) {

    /** Atalho sem justificativa (aluno presente, professor da aula ou sem registro). */
    public PresencaItem(Long alunoId, String alunoNome, boolean presente,
                        boolean trouxeBiblia, boolean trouxeRevista, boolean estudouLicao,
                        boolean professorDaAula) {
        this(alunoId, alunoNome, presente, trouxeBiblia, trouxeRevista, estudouLicao,
                professorDaAula, false, null);
    }
}
