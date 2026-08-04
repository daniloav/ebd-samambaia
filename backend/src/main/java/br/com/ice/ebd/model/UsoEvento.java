package br.com.ice.ebd.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Uso de uma funcionalidade do app: abertura de uma tela (ABRIR) ou clique notável (CLICAR).
 * Complementa o {@link AcessoEvento} (que só registra o login) para o item D do painel /uso.
 */
@Entity
@Table(name = "uso_evento")
public class UsoEvento {

    /** Ação instrumentada: abrir uma tela ou disparar um clique notável (export, WhatsApp...). */
    public enum Acao { ABRIR, CLICAR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();

    /** Chave curta da tela/ação (ex.: "chamada", "desafios", "export-pdf", "whatsapp-parabenizar"). */
    @Column(nullable = false, length = 60)
    private String recurso;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Acao acao = Acao.ABRIR;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }

    public String getRecurso() { return recurso; }
    public void setRecurso(String recurso) { this.recurso = recurso; }

    public Acao getAcao() { return acao; }
    public void setAcao(Acao acao) { this.acao = acao; }
}
