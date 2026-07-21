package br.com.ice.ebd.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Relatório de visitantes num período. Quando {@code classeId} é nulo é o consolidado
 * geral (todas as turmas — só ADMIN); caso contrário, restrito à turma informada.
 */
public record RelatorioVisitantesResponse(
        LocalDate inicio,
        LocalDate fim,
        Long classeId,
        String classeNome,
        int total,
        List<Item> itens) {

    public record Item(
            Long id,
            String nome,
            String email,
            String telefone,
            String turma,
            LocalDate dataAula,
            String trazidoPorNome) {}
}
