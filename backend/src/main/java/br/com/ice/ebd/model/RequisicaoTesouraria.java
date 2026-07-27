package br.com.ice.ebd.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Requisição de recurso à tesouraria, do líder de um ministério. */
@Entity
@Table(name = "requisicao_tesouraria")
public class RequisicaoTesouraria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String numero; // REQ-2026-0001

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false)
    private Usuario solicitante;

    @Column(nullable = false, length = 120)
    private String ministerio;

    @Column(name = "nome_evento", length = 160)
    private String nomeEvento;

    @Column(nullable = false, length = 300)
    private String destinacao;

    @Column(nullable = false, columnDefinition = "text")
    private String motivo;

    @Column(name = "valor_solicitado", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorSolicitado;

    @Column(name = "data_necessidade")
    private LocalDate dataNecessidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_repasse", nullable = false, length = 10)
    private FormaRepasse formaRepasse = FormaRepasse.DINHEIRO;

    /** Tipo da chave PIX (só quando forma = PIX). Nunca ALEATORIA. */
    @Enumerated(EnumType.STRING)
    @Column(name = "pix_tipo", length = 12)
    private TipoChavePix pixTipo;

    @Column(name = "pix_chave", length = 140)
    private String pixChave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StatusRequisicao status = StatusRequisicao.ABERTA;

    @Column(name = "valor_aprovado", precision = 12, scale = 2)
    private BigDecimal valorAprovado;

    @Column(name = "parecer_tesoureiro", length = 500)
    private String parecerTesoureiro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avaliado_por_id")
    private Usuario avaliadoPor;

    @Column(name = "avaliado_em")
    private LocalDateTime avaliadoEm;

    @Column(name = "valor_gasto", precision = 12, scale = 2)
    private BigDecimal valorGasto;

    @Column(name = "observacao_final", length = 500)
    private String observacaoFinal;

    @Column(name = "finalizado_em")
    private LocalDateTime finalizadoEm;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    /** Último dia em que o lembrete de nota fiscal foi enviado (dedup diário). */
    @Column(name = "nota_cobrada_em")
    private LocalDate notaCobradaEm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public Usuario getSolicitante() { return solicitante; }
    public void setSolicitante(Usuario solicitante) { this.solicitante = solicitante; }
    public String getMinisterio() { return ministerio; }
    public void setMinisterio(String ministerio) { this.ministerio = ministerio; }
    public String getNomeEvento() { return nomeEvento; }
    public void setNomeEvento(String nomeEvento) { this.nomeEvento = nomeEvento; }
    public String getDestinacao() { return destinacao; }
    public void setDestinacao(String destinacao) { this.destinacao = destinacao; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public BigDecimal getValorSolicitado() { return valorSolicitado; }
    public void setValorSolicitado(BigDecimal valorSolicitado) { this.valorSolicitado = valorSolicitado; }
    public LocalDate getDataNecessidade() { return dataNecessidade; }
    public void setDataNecessidade(LocalDate dataNecessidade) { this.dataNecessidade = dataNecessidade; }

    public FormaRepasse getFormaRepasse() { return formaRepasse; }
    public void setFormaRepasse(FormaRepasse formaRepasse) { this.formaRepasse = formaRepasse; }

    public TipoChavePix getPixTipo() { return pixTipo; }
    public void setPixTipo(TipoChavePix pixTipo) { this.pixTipo = pixTipo; }

    public String getPixChave() { return pixChave; }
    public void setPixChave(String pixChave) { this.pixChave = pixChave; }
    public StatusRequisicao getStatus() { return status; }
    public void setStatus(StatusRequisicao status) { this.status = status; }
    public BigDecimal getValorAprovado() { return valorAprovado; }
    public void setValorAprovado(BigDecimal valorAprovado) { this.valorAprovado = valorAprovado; }
    public String getParecerTesoureiro() { return parecerTesoureiro; }
    public void setParecerTesoureiro(String parecerTesoureiro) { this.parecerTesoureiro = parecerTesoureiro; }
    public Usuario getAvaliadoPor() { return avaliadoPor; }
    public void setAvaliadoPor(Usuario avaliadoPor) { this.avaliadoPor = avaliadoPor; }
    public LocalDateTime getAvaliadoEm() { return avaliadoEm; }
    public void setAvaliadoEm(LocalDateTime avaliadoEm) { this.avaliadoEm = avaliadoEm; }
    public BigDecimal getValorGasto() { return valorGasto; }
    public void setValorGasto(BigDecimal valorGasto) { this.valorGasto = valorGasto; }
    public String getObservacaoFinal() { return observacaoFinal; }
    public void setObservacaoFinal(String observacaoFinal) { this.observacaoFinal = observacaoFinal; }
    public LocalDateTime getFinalizadoEm() { return finalizadoEm; }
    public void setFinalizadoEm(LocalDateTime finalizadoEm) { this.finalizadoEm = finalizadoEm; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
    public LocalDate getNotaCobradaEm() { return notaCobradaEm; }
    public void setNotaCobradaEm(LocalDate notaCobradaEm) { this.notaCobradaEm = notaCobradaEm; }
}
