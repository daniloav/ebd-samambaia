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
        boolean possuiComprovante,
        List<AnexoResumo> anexos) {

    public record AnexoResumo(Long id, String nome, String tipo, String categoria) {}

    /** Nome de exibição de um usuário: o nome do aluno vinculado, ou o login. */
    public static String nomeDe(Usuario u) {
        if (u == null) {
            return null;
        }
        return u.getAluno() != null ? u.getAluno().getNome() : u.getUsername();
    }

    public static RequisicaoResponse de(RequisicaoTesouraria r, List<RequisicaoAnexo> anexos) {
        boolean temComp = anexos != null && anexos.stream()
                .anyMatch(a -> a.getCategoria() == CategoriaAnexo.COMPROVANTE);
        return de(r, anexos, temComp);
    }

    /** Variante para a listagem: possuiComprovante vem de uma query leve (sem carregar binário). */
    public static RequisicaoResponse de(RequisicaoTesouraria r, List<RequisicaoAnexo> anexos, boolean possuiComprovante) {
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
                possuiComprovante,
                as);
    }
}
