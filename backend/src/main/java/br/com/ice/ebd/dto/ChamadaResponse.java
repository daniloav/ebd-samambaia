package br.com.ice.ebd.dto;

import java.time.LocalDate;
import java.util.List;

/** Chamada completa de uma aula: dados da aula + lista de alunos com seus itens. */
public record ChamadaResponse(
        Long aulaId,
        LocalDate data,
        String tema,
        List<PresencaItem> itens,
        Integer emailsEnviados) {

    /** Construtor sem contagem (usado ao apenas montar a chamada — GET). */
    public ChamadaResponse(Long aulaId, LocalDate data, String tema, List<PresencaItem> itens) {
        this(aulaId, data, tema, itens, null);
    }

    /** Cópia com a quantidade de e-mails novos enviados no salvamento. */
    public ChamadaResponse comEmailsEnviados(int n) {
        return new ChamadaResponse(aulaId, data, tema, itens, n);
    }
}
