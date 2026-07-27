package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.RequisicaoAnexo;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class RequisicaoAnexoRepository implements PanacheRepository<RequisicaoAnexo> {

    public List<RequisicaoAnexo> listarPorRequisicao(Long requisicaoId) {
        return list("requisicao.id", requisicaoId);
    }

    /** Ids das requisições (dentre as informadas) que já têm um comprovante anexado — leve, sem binário. */
    public java.util.List<Long> idsComComprovante(java.util.Collection<Long> requisicaoIds) {
        if (requisicaoIds == null || requisicaoIds.isEmpty()) {
            return java.util.List.of();
        }
        return getEntityManager().createQuery(
                "select distinct a.requisicao.id from RequisicaoAnexo a "
                + "where a.categoria = br.com.ice.ebd.model.CategoriaAnexo.COMPROVANTE "
                + "and a.requisicao.id in :ids", Long.class)
                .setParameter("ids", requisicaoIds)
                .getResultList();
    }
}
