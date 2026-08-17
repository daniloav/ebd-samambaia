package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.MotivoInativacao;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Relatório de alunos inativados. Uma linha por <b>episódio</b> de inativação (o mesmo aluno
 * pode aparecer mais de uma vez se saiu e voltou mais de uma vez). Com {@code classeId} nulo
 * é o consolidado geral (todas as turmas — só ADMIN).
 *
 * @param periodoAberto  sem filtro de período: entram também os episódios sem data (histórico
 *                       anterior à V30, que só existia como {@code ativo = false})
 * @param semDataRegistrada quantos episódios do escopo não têm data de inativação — quando o
 *                       período é filtrado eles ficam de fora, e a tela avisa
 */
public record RelatorioInativadosResponse(
        LocalDate inicio,
        LocalDate fim,
        boolean periodoAberto,
        Long classeId,
        String classeNome,
        int total,
        int aindaInativos,
        int reativados,
        int porFaltasSeguidas,
        int manuais,
        long semDataRegistrada,
        List<Item> itens) {

    public record Item(
            Long alunoId,
            String nome,
            String turma,
            String email,
            String telefone,
            LocalDateTime inativadoEm,
            MotivoInativacao motivo,
            Integer faltasSeguidas,
            String inativadoPor,
            LocalDate ultimaPresenca,
            LocalDateTime reativadoEm) {

        /** O aluno voltou (o episódio está fechado). */
        public boolean reativado() { return reativadoEm != null; }
    }
}
