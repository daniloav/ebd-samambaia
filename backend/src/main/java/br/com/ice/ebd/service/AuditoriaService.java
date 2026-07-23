package br.com.ice.ebd.service;

import br.com.ice.ebd.model.AcaoAuditoria;
import br.com.ice.ebd.model.Auditoria;
import br.com.ice.ebd.model.EntidadeAuditoria;
import br.com.ice.ebd.repository.AuditoriaRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Auditoria de ações de gestão. O {@link #registrar} é chamado pelos serviços de CRUD dentro
 * da <b>mesma transação</b> da operação — então o log só grava se a ação de fato aconteceu
 * (e reverte junto se a operação falhar). Guarda quem/quando/ação/entidade/registro (rótulo),
 * sem diff de campos.
 */
@ApplicationScoped
public class AuditoriaService {

    @Inject SecurityIdentity identity;
    @Inject AuditoriaRepository repository;

    /** Rótulo é truncado com segurança para caber na coluna (200). */
    public void registrar(AcaoAuditoria acao, EntidadeAuditoria entidade, Long entidadeId, String descricao) {
        Auditoria a = new Auditoria();
        a.setDataHora(LocalDateTime.now());
        a.setUsuario(usuarioAtual());
        a.setAcao(acao);
        a.setEntidade(entidade);
        a.setEntidadeId(entidadeId);
        a.setDescricao(descricao == null ? null : (descricao.length() > 200 ? descricao.substring(0, 200) : descricao));
        repository.persist(a);
    }

    public List<Auditoria> listar(EntidadeAuditoria entidade, LocalDateTime inicio, LocalDateTime fim, int limite) {
        int lim = (limite <= 0 || limite > 1000) ? 300 : limite;
        return repository.listar(entidade, inicio, fim, lim);
    }

    private String usuarioAtual() {
        try {
            String u = identity.getPrincipal() != null ? identity.getPrincipal().getName() : null;
            return (u == null || u.isBlank()) ? "sistema" : u;
        } catch (Exception e) {
            return "sistema";
        }
    }
}
