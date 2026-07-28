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
        int faltasJustificadas,
        List<Item> itens) {

    public record Item(
            Long aulaId,
            LocalDate data,
            String tema,
            boolean presente,
            boolean trouxeBiblia,
            boolean trouxeRevista,
            boolean estudouLicao,
            /** Falta já justificada por este aluno. */
            boolean justificada,
            String justificativaMotivo,
            /** true quando é uma falta (ausente) e pode receber justificativa. */
            boolean podeJustificar) {
    }
}
