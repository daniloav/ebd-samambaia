package br.com.ice.ebd.service;

import br.com.ice.ebd.dto.AvaliarRequest;
import br.com.ice.ebd.dto.RequisicaoRequest;
import br.com.ice.ebd.dto.RequisicaoResponse;
import br.com.ice.ebd.model.CategoriaAnexo;
import br.com.ice.ebd.model.FormaRepasse;
import br.com.ice.ebd.model.RequisicaoAnexo;
import br.com.ice.ebd.model.RequisicaoTesouraria;
import br.com.ice.ebd.model.TipoChavePix;
import br.com.ice.ebd.model.TitularChavePix;
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
    public record AnexoData(String nome, String tipo, byte[] conteudo, CategoriaAnexo categoria) {}

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
        aplicarRepasse(r, req, solicitante);
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
        java.util.Set<Long> comComprovante = new java.util.HashSet<>(
                anexoRepository.idsComComprovante(reqs.stream().map(RequisicaoTesouraria::getId).toList()));
        return reqs.stream()
                .map(r -> RequisicaoResponse.de(r, List.of(), comComprovante.contains(r.getId())))
                .toList();
    }

    @Transactional
    public RequisicaoResponse buscar(Long id) {
        RequisicaoTesouraria r = obter(id);
        assertPodeVer(r);
        return RequisicaoResponse.de(r, anexoRepository.listarPorRequisicao(id));
    }

    @Transactional
    public RequisicaoResponse aprovar(Long id, BigDecimal valorAprovado, String parecer, AnexoData comprovante) {
        RequisicaoTesouraria r = obter(id);
        exigirStatus(r, StatusRequisicao.ABERTA, "Só é possível aprovar uma requisição em aberto.");
        BigDecimal valor = valorAprovado != null ? valorAprovado : r.getValorSolicitado();
        if (valor.signum() <= 0) {
            throw bad("O valor aprovado deve ser maior que zero.");
        }
        r.setValorAprovado(valor);
        r.setParecerTesoureiro(trunc(parecer));
        r.setAvaliadoPor(usuarioAtual());
        r.setAvaliadoEm(LocalDateTime.now());
        r.setStatus(StatusRequisicao.APROVADA);
        if (comprovante != null) {
            persistAnexo(r, comprovante);
        }
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
    public RequisicaoResponse finalizar(Long id, BigDecimal valorGasto, String observacao,
            List<AnexoData> anexos, AnexoData comprovanteTroco) {
        RequisicaoTesouraria r = obter(id);
        assertDonoOuAdmin(r);
        exigirStatus(r, StatusRequisicao.APROVADA, "Só é possível finalizar uma requisição aprovada.");
        // Oferta de amor (PIX para terceiro): não há nota fiscal — a prestação de contas é o
        // comprovante da transferência, que pode já ter sido anexado pelo tesoureiro ao aprovar.
        boolean semNotaFiscal = r.isPixParaTerceiro();
        if (anexos == null || anexos.isEmpty()) {
            if (!semNotaFiscal) {
                throw bad("Anexe ao menos a nota fiscal para finalizar.");
            }
            if (anexoRepository.idsComComprovante(List.of(r.getId())).isEmpty()) {
                throw bad("Anexe o comprovante da transferência ao beneficiário para finalizar.");
            }
        }
        // Gastou menos que o aprovado? O troco volta ao PIX da igreja e o comprovante
        // dessa devolução é obrigatório para finalizar.
        BigDecimal troco = trocoDevido(r, valorGasto);
        if (troco.signum() > 0 && comprovanteTroco == null) {
            throw bad("Há troco de R$ " + troco.toPlainString().replace('.', ',')
                    + " a devolver — anexe o comprovante da transferência do troco ao PIX da igreja.");
        }
        if (anexos != null) {
            for (AnexoData ad : anexos) {
                // sem nota fiscal, o que o líder anexa aqui é o comprovante da transferência
                persistAnexo(r, semNotaFiscal
                        ? new AnexoData(ad.nome(), ad.tipo(), ad.conteudo(), CategoriaAnexo.COMPROVANTE)
                        : ad);
            }
        }
        if (comprovanteTroco != null) {
            persistAnexo(r, comprovanteTroco);
        }
        r.setValorGasto(valorGasto);
        r.setObservacaoFinal(trunc(observacao));
        r.setStatus(StatusRequisicao.FINALIZADA);
        r.setFinalizadoEm(LocalDateTime.now());
        notificacao.avisarRequisicaoFinalizada(r, usuarioRepository.emailsDeTesoureirosAtivos());
        return RequisicaoResponse.de(r, anexoRepository.listarPorRequisicao(id));
    }

    /** Troco a devolver: valor aprovado − valor gasto (0 se gastou tudo, mais, ou não informou). */
    private BigDecimal trocoDevido(RequisicaoTesouraria r, BigDecimal valorGasto) {
        if (valorGasto == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal referencia = r.getValorAprovado() != null ? r.getValorAprovado() : r.getValorSolicitado();
        BigDecimal troco = referencia.subtract(valorGasto).setScale(2, java.math.RoundingMode.HALF_UP);
        return troco.signum() > 0 ? troco : BigDecimal.ZERO;
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

    private void persistAnexo(RequisicaoTesouraria r, AnexoData ad) {
        RequisicaoAnexo a = new RequisicaoAnexo();
        a.setRequisicao(r);
        a.setNome(ad.nome());
        a.setTipo(ad.tipo() != null ? ad.tipo() : "application/octet-stream");
        a.setConteudo(ad.conteudo());
        a.setCategoria(ad.categoria() != null ? ad.categoria() : CategoriaAnexo.NOTA_FISCAL);
        anexoRepository.persist(a);
    }

    /**
     * Forma de repasse + validação da chave PIX. A chave é do próprio solicitante (conferida
     * contra o cadastro) ou de um <b>terceiro beneficiado</b> — oferta de amor, em que o recurso
     * vai direto para a conta de quem está sendo ajudado; aí só se valida o formato da chave e
     * exige-se o nome do beneficiário, que o tesoureiro confere no comprovante do banco.
     */
    private void aplicarRepasse(RequisicaoTesouraria r, RequisicaoRequest req, Usuario dono) {
        FormaRepasse forma = FormaRepasse.DINHEIRO;
        if (req.formaRepasse() != null && !req.formaRepasse().isBlank()) {
            try {
                forma = FormaRepasse.valueOf(req.formaRepasse().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw bad("Forma de repasse inválida.");
            }
        }
        r.setFormaRepasse(forma);
        if (forma != FormaRepasse.PIX) {
            r.setPixTipo(null);
            r.setPixChave(null);
            r.setPixTitular(TitularChavePix.PROPRIO);
            r.setPixBeneficiarioNome(null);
            r.setPixBeneficiarioObs(null);
            return;
        }
        TipoChavePix tipo = parseTipoPix(req.pixTipo());
        String chave = req.pixChave() != null ? req.pixChave().trim() : "";
        if (chave.isBlank()) {
            throw bad("Informe a chave PIX.");
        }
        TitularChavePix titular = parseTitularPix(req.pixTitular());
        if (titular == TitularChavePix.TERCEIRO) {
            String nome = req.pixBeneficiarioNome() != null ? req.pixBeneficiarioNome().trim() : "";
            if (nome.isBlank()) {
                throw bad("Informe o nome do beneficiário — a chave PIX não é sua.");
            }
            validarFormatoDaChave(tipo, chave);
            r.setPixBeneficiarioNome(nome);
            String obs = req.pixBeneficiarioObs() != null ? req.pixBeneficiarioObs().trim() : "";
            r.setPixBeneficiarioObs(obs.isBlank() ? null : obs);
        } else {
            validarChaveDoDono(tipo, chave, dono);
            r.setPixBeneficiarioNome(null);
            r.setPixBeneficiarioObs(null);
        }
        r.setPixTitular(titular);
        r.setPixTipo(tipo);
        r.setPixChave(chave);
    }

    private TitularChavePix parseTitularPix(String s) {
        if (s == null || s.isBlank()) {
            return TitularChavePix.PROPRIO;
        }
        try {
            return TitularChavePix.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw bad("Titular da chave PIX inválido — use PROPRIO ou TERCEIRO.");
        }
    }

    private TipoChavePix parseTipoPix(String s) {
        if (s == null || s.isBlank()) {
            throw bad("Informe o tipo da chave PIX.");
        }
        String v = java.text.Normalizer.normalize(s.trim().toUpperCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        if (v.equals("ALEATORIA")) {
            throw bad("Chave aleatória não é aceita — use CPF, e-mail ou telefone.");
        }
        try {
            return TipoChavePix.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw bad("Tipo de chave PIX inválido — use CPF, e-mail ou telefone.");
        }
    }

    /** A chave PIX precisa ser do próprio solicitante (nada de terceiros). */
    private void validarChaveDoDono(TipoChavePix tipo, String chave, Usuario dono) {
        switch (tipo) {
            case EMAIL -> {
                String email = dono.getEmail();
                if (email == null || email.isBlank()) {
                    throw bad("Seu cadastro não tem e-mail; cadastre-o ou use outra chave sua.");
                }
                if (!email.trim().equalsIgnoreCase(chave)) {
                    throw bad("A chave de e-mail deve ser o seu próprio e-mail cadastrado.");
                }
            }
            case TELEFONE -> {
                String tel = dono.getAluno() != null ? dono.getAluno().getTelefone() : null;
                if (tel == null || tel.isBlank()) {
                    throw bad("Não há um telefone seu cadastrado; use outra chave sua.");
                }
                if (!digitos(tel).equals(digitos(chave))) {
                    throw bad("A chave de telefone deve ser o seu próprio telefone cadastrado.");
                }
            }
            case CPF -> {
                // Não guardamos CPF, então não há como cross-validar a titularidade aqui;
                // exige-se ao menos um CPF bem formado e o tesoureiro confere no comprovante.
                if (digitos(chave).length() != 11) {
                    throw bad("CPF inválido — informe os 11 dígitos.");
                }
            }
        }
    }

    /**
     * Chave de terceiro: não há como conferir a titularidade no nosso cadastro, então validamos
     * só o formato (o tesoureiro confere o nome do beneficiário no comprovante do banco).
     */
    private void validarFormatoDaChave(TipoChavePix tipo, String chave) {
        switch (tipo) {
            case EMAIL -> {
                if (!chave.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}")) {
                    throw bad("E-mail inválido para a chave PIX.");
                }
            }
            case TELEFONE -> {
                String d = digitos(chave);
                if (d.length() < 10 || d.length() > 13) {
                    throw bad("Telefone inválido — informe com DDD.");
                }
            }
            case CPF -> {
                if (digitos(chave).length() != 11) {
                    throw bad("CPF inválido — informe os 11 dígitos.");
                }
            }
        }
    }

    private static String digitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    /**
     * Próximo número do ano: {@code REQ-<ano>-<seq4>}. O sequencial vem do <b>maior já usado</b>
     * (não da contagem) — se alguma requisição for apagada, a contagem encolhe e reemitiria um
     * número existente, quebrando a unique de {@code numero} e derrubando a abertura de novas
     * requisições.
     */
    private String gerarNumero() {
        String prefixo = "REQ-" + Year.now().getValue() + "-";
        long seq = repository.maiorSequenciaComPrefixo(prefixo) + 1;
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
        return escopo.isAdmin() || u.isEhTesoureiro();
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
