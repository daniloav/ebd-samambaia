package br.com.ice.ebd.dto;

/** Linha da chamada: um aluno e seus itens avaliados em uma aula. */
public record PresencaItem(
        Long alunoId,
        String alunoNome,
        boolean presente,
        boolean trouxeBiblia,
        boolean trouxeRevista,
        boolean estudouLicao,
        boolean trouxeVisitante) {
}
