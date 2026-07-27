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

/** Anexo (nota fiscal / comprovante) de uma requisição — guardado no banco. */
@Entity
@Table(name = "requisicao_anexo")
public class RequisicaoAnexo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requisicao_id", nullable = false)
    private RequisicaoTesouraria requisicao;

    @Column(length = 200)
    private String nome;

    @Column(nullable = false, length = 100)
    private String tipo;

    @Column(nullable = false)
    private byte[] conteudo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private CategoriaAnexo categoria = CategoriaAnexo.NOTA_FISCAL;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public RequisicaoTesouraria getRequisicao() { return requisicao; }
    public void setRequisicao(RequisicaoTesouraria requisicao) { this.requisicao = requisicao; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public byte[] getConteudo() { return conteudo; }
    public void setConteudo(byte[] conteudo) { this.conteudo = conteudo; }
    public CategoriaAnexo getCategoria() { return categoria; }
    public void setCategoria(CategoriaAnexo categoria) { this.categoria = categoria; }
}
