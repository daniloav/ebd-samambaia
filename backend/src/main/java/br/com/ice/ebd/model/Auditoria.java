package br.com.ice.ebd.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Registro de auditoria: quem fez o quê, quando, em qual entidade/registro. */
@Entity
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();

    @Column(nullable = false, length = 60)
    private String usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AcaoAuditoria acao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntidadeAuditoria entidade;

    @Column(name = "entidade_id")
    private Long entidadeId;

    @Column(length = 200)
    private String descricao;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public AcaoAuditoria getAcao() { return acao; }
    public void setAcao(AcaoAuditoria acao) { this.acao = acao; }

    public EntidadeAuditoria getEntidade() { return entidade; }
    public void setEntidade(EntidadeAuditoria entidade) { this.entidade = entidade; }

    public Long getEntidadeId() { return entidadeId; }
    public void setEntidadeId(Long entidadeId) { this.entidadeId = entidadeId; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
}
