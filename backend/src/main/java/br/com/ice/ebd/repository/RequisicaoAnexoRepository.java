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
}
