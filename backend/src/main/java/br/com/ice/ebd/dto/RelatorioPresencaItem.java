package br.com.ice.ebd.dto;

/** Resumo de presenças de um aluno no período consultado. */
public record RelatorioPresencaItem(
        Long alunoId,
        String nome,
        long totalAulas,
        long presencas,
        long faltas,
        double percentualPresenca,
        long trouxeBiblia,
        long trouxeRevista,
        long estudouLicao,
        long trouxeVisitante) {
}
