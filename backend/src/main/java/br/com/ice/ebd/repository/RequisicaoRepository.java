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

    /** Quantidade de requisições cujo número começa com o prefixo (para gerar o sequencial do ano). */
    public long contarComPrefixo(String prefixo) {
        return count("numero like ?1", prefixo + "%");
    }
}
