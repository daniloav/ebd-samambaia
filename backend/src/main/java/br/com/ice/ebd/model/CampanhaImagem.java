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

/** Imagem/arte de uma campanha (guardada no banco e embutida inline no e-mail). */
@Entity
@Table(name = "campanha_imagem")
public class CampanhaImagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campanha_id", nullable = false)
    private Campanha campanha;

    @Column(length = 200)
    private String nome;

    @Column(nullable = false, length = 100)
    private String tipo;

    @Column(nullable = false)
    private byte[] conteudo;

    @Column(nullable = false)
    private int ordem;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Campanha getCampanha() { return campanha; }
    public void setCampanha(Campanha campanha) { this.campanha = campanha; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public byte[] getConteudo() { return conteudo; }
    public void setConteudo(byte[] conteudo) { this.conteudo = conteudo; }

    public int getOrdem() { return ordem; }
    public void setOrdem(int ordem) { this.ordem = ordem; }
}
