package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.RequisicaoTesouraria;
import br.com.ice.ebd.model.StatusRequisicao;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class RequisicaoRepository implements PanacheRepository<RequisicaoTesouraria> {

    /** Todas (tesoureiro/admin), com filtro opcional por status, mais recentes primeiro. */
    public List<RequisicaoTesouraria> listar(StatusRequisicao status) {
        return status == null ? listAll(Sort.by("criadoEm").descending())
                : list("status = ?1 order by criadoEm desc", status);
    }

    /** Só as do solicitante (líder), com filtro opcional por status. */
    public List<RequisicaoTesouraria> listarDoSolicitante(Long solicitanteId, StatusRequisicao status) {
        return status == null ? list("solicitante.id = ?1 order by criadoEm desc", solicitanteId)
                : list("solicitante.id = ?1 and status = ?2 order by criadoEm desc", solicitanteId, status);
    }

    /** Aprovadas ainda sem prestação de contas (para o lembrete diário). */
    public List<RequisicaoTesouraria> aprovadasPendentesDeNota() {
        return list("status", StatusRequisicao.APROVADA);
    }

    /**
     * Maior sequencial já usado no prefixo do ano (0 se não houver nenhum) — base para o próximo
     * número. É o <b>máximo</b>, e não a contagem: se alguma requisição for apagada (ex.: limpeza
     * de dados de teste), a contagem encolhe e voltaria a gerar um número já existente, estourando
     * a unique de {@code numero}.
     */
    public long maiorSequenciaComPrefixo(String prefixo) {
        Integer max = getEntityManager().createQuery(
                "select max(cast(substring(r.numero, :corte) as integer)) from RequisicaoTesouraria r "
                + "where r.numero like :prefixo", Integer.class)
                .setParameter("corte", prefixo.length() + 1)
                .setParameter("prefixo", prefixo + "%")
                .getSingleResult();
        return max == null ? 0L : max;
    }

    /** Quantas requisições este usuário abriu (solicitante) — bloqueia a exclusão do usuário. */
    public long contarComoSolicitante(Long usuarioId) {
        return count("solicitante.id", usuarioId);
    }
}
