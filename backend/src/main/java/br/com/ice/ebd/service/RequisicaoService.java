package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.AvaliarRequest;
import br.com.ice.ebd.dto.RequisicaoRequest;
import br.com.ice.ebd.dto.RequisicaoResponse;
import br.com.ice.ebd.model.RequisicaoAnexo;
import br.com.ice.ebd.model.RequisicaoTesouraria;
import br.com.ice.ebd.model.Role;
import br.com.ice.ebd.model.StatusRequisicao;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.repository.RequisicaoAnexoRepository;
import br.com.ice.ebd.repository.RequisicaoRepository;
import br.com.ice.ebd.repository.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

/**
 * Requisições da tesouraria: abertura (líder), avaliação (tesoureiro), finalização com nota
 * (líder). Transições validadas; cada evento dispara e-mail pelo {@link NotificacaoService}.
 */
@ApplicationScoped
public class RequisicaoService {

    @Inject EscopoService escopo;
    @Inject RequisicaoRepository repository;
    @Inject RequisicaoAnexoRepository anexoRepository;
    @Inject UsuarioRepository usuarioRepository;
    @Inject NotificacaoService notificacao;

    /** Dados de um anexo recebido no upload (montado no resource). */
    public record AnexoData(String nome, String tipo, byte[] conteudo) {}

    @Transactional
    public RequisicaoResponse criar(RequisicaoRequest req) {
        Usuario solicitante = usuarioAtual();
        RequisicaoTesouraria r = new RequisicaoTesouraria();
        r.setNumero(gerarNumero());
        r.setSolicitante(solicitante);
        r.setMinisterio(req.ministerio().trim());
        r.setNomeEvento(req.nomeEvento() != null && !req.nomeEvento().isBlank() ? req.nomeEvento().trim() : null);
        r.setDestinacao(req.destinacao().trim());
        r.setMotivo(req.motivo().trim());
        r.setValorSolicitado(req.valorSolicitado());
        r.setDataNecessidade(req.dataNecessidade());
        r.setStatus(StatusRequisicao.ABERTA);
        r.setCriadoEm(LocalDateTime.now());
        repository.persist(r);

        notificacao.avisarNovaRequisicao(r, usuarioRepository.emailsDeTesoureirosAtivos());
        return RequisicaoResponse.de(r, List.of());
    }

    @Transactional
    public List<RequisicaoResponse> listar(String statusFiltro) {
        Usuario u = usuarioAtual();
        StatusRequisicao status = parseStatus(statusFiltro);
        List<RequisicaoTesouraria> reqs = podeVerTodas(u)
                ? repository.listar(status)
                : repository.listarDoSolicitante(u.getId(), status);
        return reqs.stream().map(r -> RequisicaoResponse.de(r, List.of())).toList();
    }

    @Transactional
    public RequisicaoResponse buscar(Long id) {
        RequisicaoTesouraria r = obter(id);
        assertPodeVer(r);
        return RequisicaoResponse.de(r, anexoRepository.listarPorRequisicao(id));
    }

    @Transactional
    public RequisicaoResponse aprovar(Long id, AvaliarRequest req) {
        RequisicaoTesouraria r = obter(id);
        exigirStatus(r, StatusRequisicao.ABERTA, "Só é possível aprovar uma requisição em aberto.");
        BigDecimal valor = req != null && req.valorAprovado() != null ? req.valorAprovado() : r.getValorSolicitado();
        if (valor.signum() <= 0) {
            throw bad("O valor aprovado deve ser maior que zero.");
        }
        r.setValorAprovado(valor);
        r.setParecerTesoureiro(req != null ? trunc(req.parecer()) : null);
        r.setAvaliadoPor(usuarioAtual());
        r.setAvaliadoEm(LocalDateTime.now());
        r.setStatus(StatusRequisicao.APROVADA);
        notificacao.avisarRequisicaoAvaliada(r);
        return RequisicaoResponse.de(r, anexoRepository.listarPorRequisicao(id));
    }

    @Transactional
    public RequisicaoResponse negar(Long id, AvaliarRequest req) {
        RequisicaoTesouraria r = obter(id);
        exigirStatus(r, StatusRequisicao.ABERTA, "Só é possível negar uma requisição em aberto.");
        r.setParecerTesoureiro(req != null ? trunc(req.parecer()) : null);
        r.setAvaliadoPor(usuarioAtual());
        r.setAvaliadoEm(LocalDateTime.now());
        r.setStatus(StatusRequisicao.NEGADA);
        notificacao.avisarRequisicaoAvaliada(r);
        return RequisicaoResponse.de(r, anexoRepository.listarPorRequisicao(id));
    }

    @Transactional
    public RequisicaoResponse finalizar(Long id, BigDecimal valorGasto, String observacao, List<AnexoData> anexos) {
        RequisicaoTesouraria r = obter(id);
        assertDonoOuAdmin(r);
        exigirStatus(r, StatusRequisicao.APROVADA, "Só é possível finalizar uma requisição aprovada.");
        if (anexos == null || anexos.isEmpty()) {
            throw bad("Anexe ao menos a nota fiscal para finalizar.");
        }
        for (AnexoData ad : anexos) {
            RequisicaoAnexo a = new RequisicaoAnexo();
            a.setRequisicao(r);
            a.setNome(ad.nome());
            a.setTipo(ad.tipo() != null ? ad.tipo() : "application/octet-stream");
            a.setConteudo(ad.conteudo());
            anexoRepository.persist(a);
        }
        r.setValorGasto(valorGasto);
        r.setObservacaoFinal(trunc(observacao));
        r.setStatus(StatusRequisicao.FINALIZADA);
        r.setFinalizadoEm(LocalDateTime.now());
        notificacao.avisarRequisicaoFinalizada(r, usuarioRepository.emailsDeTesoureirosAtivos());
        return RequisicaoResponse.de(r, anexoRepository.listarPorRequisicao(id));
    }

    @Transactional
    public RequisicaoResponse cancelar(Long id) {
        RequisicaoTesouraria r = obter(id);
        assertDonoOuAdmin(r);
        exigirStatus(r, StatusRequisicao.ABERTA, "Só é possível cancelar uma requisição em aberto.");
        r.setStatus(StatusRequisicao.CANCELADA);
        return RequisicaoResponse.de(r, anexoRepository.listarPorRequisicao(id));
    }

    /** Anexo (binário) para download; valida o acesso. */
    @Transactional
    public RequisicaoAnexo obterAnexo(Long anexoId) {
        RequisicaoAnexo a = anexoRepository.findById(anexoId);
        if (a == null) {
            throw new NotFoundException("Anexo não encontrado.");
        }
        assertPodeVer(a.getRequisicao());
        return a;
    }

    // ---------- helpers ----------

    private String gerarNumero() {
        String prefixo = "REQ-" + Year.now().getValue() + "-";
        long seq = repository.contarComPrefixo(prefixo) + 1;
        return prefixo + String.format("%04d", seq);
    }

    private Usuario usuarioAtual() {
        Usuario u = escopo.usuarioLogado();
        if (u == null) {
            throw new ForbiddenException("Usuário não identificado.");
        }
        return u;
    }

    private boolean podeVerTodas(Usuario u) {
        return escopo.isAdmin() || u.getRole() == Role.TESOUREIRO;
    }

    private void assertPodeVer(RequisicaoTesouraria r) {
        Usuario u = usuarioAtual();
        if (!podeVerTodas(u) && !r.getSolicitante().getId().equals(u.getId())) {
            throw new ForbiddenException("Você não tem acesso a esta requisição.");
        }
    }

    private void assertDonoOuAdmin(RequisicaoTesouraria r) {
        Usuario u = usuarioAtual();
        if (!escopo.isAdmin() && !r.getSolicitante().getId().equals(u.getId())) {
            throw new ForbiddenException("Apenas o solicitante pode fazer esta ação.");
        }
    }

    private RequisicaoTesouraria obter(Long id) {
        RequisicaoTesouraria r = repository.findById(id);
        if (r == null) {
            throw new NotFoundException("Requisição não encontrada: " + id);
        }
        return r;
    }

    private void exigirStatus(RequisicaoTesouraria r, StatusRequisicao esperado, String msg) {
        if (r.getStatus() != esperado) {
            throw bad(msg + " (status atual: " + r.getStatus() + ")");
        }
    }

    private StatusRequisicao parseStatus(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return StatusRequisicao.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw bad("Status inválido: " + s);
        }
    }

    private static String trunc(String s) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        return s.length() > 500 ? s.substring(0, 500) : (s.isEmpty() ? null : s);
    }

    private WebApplicationException bad(String msg) {
        return new WebApplicationException(msg, Response.Status.BAD_REQUEST);
    }
}
