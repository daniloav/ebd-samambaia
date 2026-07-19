package br.com.ice.ebd.dto;

import java.time.LocalDate;
import java.util.List;

/** Chamada completa de uma aula: dados da aula + lista de alunos com seus itens. */
public record ChamadaResponse(
        Long aulaId,
        LocalDate data,
        String tema,
        List<PresencaItem> itens) {
}
