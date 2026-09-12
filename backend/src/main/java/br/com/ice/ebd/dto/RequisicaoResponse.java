package br.com.ice.ebd.dto;

import br.com.ice.ebd.model.CategoriaAnexo;
import br.com.ice.ebd.model.RequisicaoAnexo;
import br.com.ice.ebd.model.RequisicaoTesouraria;
import br.com.ice.ebd.model.Usuario;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RequisicaoResponse(
        Long id,
        String numero,
        String status,
        Long solicitanteId,
        String solicitanteNome,
        String ministerio,
        String nomeEvento,
        String destinacao,
        String motivo,
        BigDecimal valorSolicitado,
        LocalDate dataNecessidade,
        BigDecimal valorAprovado,
        String parecerTesoureiro,
        String avaliadoPorNome,
        LocalDateTime avaliadoEm,
        BigDecimal valorGasto,
        String observacaoFinal,
        LocalDateTime finalizadoEm,
        LocalDateTime criadoEm,
        String formaRepasse,
        String pixTipo,
        String pixChave,
        /** PROPRIO | TERCEIRO (chave do beneficiado — oferta de amor). */
        String pixTitular,
        String pixBeneficiarioNome,
        String pixBeneficiarioObs,
        boolean possuiComprovante,
        /** Quando esta requisição foi absorvida por outra, o número da principal (senão null). */
        String juntadaNaNumero,
        Long juntadaNaId,
        /** As requisições que esta absorveu (junção) — vazio quando não juntou nenhuma. */
        List<RequisicaoJuntada> juntadas,
        List<AnexoResumo> anexos) {

    public record AnexoResumo(Long id, String nome, String tipo, String categoria) {}

    /** Resumo de uma requisição absorvida, para mostrar do que o valor total é feito. */
    public record RequisicaoJuntada(Long id, String numero, BigDecimal valorSolicitado,
            String destinacao, LocalDate dataNecessidade) {

        public static RequisicaoJuntada de(RequisicaoTesouraria r) {
            return new RequisicaoJuntada(r.getId(), r.getNumero(), r.getValorSolicitado(),
                    r.getDestinacao(), r.getDataNecessidade());
        }
    }

    /** Nome de exibição de um usuário: o nome do aluno vinculado, ou o login. */
    public static String nomeDe(Usuario u) {
        if (u == null) {
            return null;
        }
        return u.getAluno() != null ? u.getAluno().getNome() : u.getUsername();
    }

    public static RequisicaoResponse de(RequisicaoTesouraria r, List<RequisicaoAnexo> anexos) {
        return de(r, anexos, List.of());
    }

    public static RequisicaoResponse de(RequisicaoTesouraria r, List<RequisicaoAnexo> anexos,
            List<RequisicaoTesouraria> juntadas) {
        boolean temComp = anexos != null && anexos.stream()
                .anyMatch(a -> a.getCategoria() == CategoriaAnexo.COMPROVANTE);
        return de(r, anexos, temComp, juntadas);
    }

    /** Variante para a listagem: possuiComprovante vem de uma query leve (sem carregar binário). */
    public static RequisicaoResponse de(RequisicaoTesouraria r, List<RequisicaoAnexo> anexos,
            boolean possuiComprovante, List<RequisicaoTesouraria> juntadas) {
        List<AnexoResumo> as = anexos == null ? List.of()
                : anexos.stream().map(a -> new AnexoResumo(a.getId(), a.getNome(), a.getTipo(),
                        a.getCategoria().name())).toList();
        return new RequisicaoResponse(
                r.getId(), r.getNumero(), r.getStatus().name(),
                r.getSolicitante().getId(), nomeDe(r.getSolicitante()),
                r.getMinisterio(), r.getNomeEvento(), r.getDestinacao(), r.getMotivo(),
                r.getValorSolicitado(), r.getDataNecessidade(),
                r.getValorAprovado(), r.getParecerTesoureiro(), nomeDe(r.getAvaliadoPor()), r.getAvaliadoEm(),
                r.getValorGasto(), r.getObservacaoFinal(), r.getFinalizadoEm(),
                r.getCriadoEm(),
                r.getFormaRepasse().name(),
                r.getPixTipo() != null ? r.getPixTipo().name() : null,
                r.getPixChave(),
                r.getPixTitular() != null ? r.getPixTitular().name() : null,
                r.getPixBeneficiarioNome(),
                r.getPixBeneficiarioObs(),
                possuiComprovante,
                r.getJuntadaNa() != null ? r.getJuntadaNa().getNumero() : null,
                r.getJuntadaNa() != null ? r.getJuntadaNa().getId() : null,
                juntadas == null ? List.of() : juntadas.stream().map(RequisicaoJuntada::de).toList(),
                as);
    }
}
