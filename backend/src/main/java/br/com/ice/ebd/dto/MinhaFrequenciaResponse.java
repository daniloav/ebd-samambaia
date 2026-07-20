package br.com.ice.ebd.dto;

import java.time.LocalDate;
import java.util.List;

/** Visão que o próprio ALUNO tem da sua frequência (não expõe dados de outros alunos). */
public record MinhaFrequenciaResponse(
        String alunoNome,
        int totalAulas,
        int presencas,
        int faltas,
        int percentualPresenca,
        List<Item> itens) {

    public record Item(
            LocalDate data,
            String tema,
            boolean presente,
            boolean trouxeBiblia,
            boolean trouxeRevista,
            boolean estudouLicao) {
    }
}
