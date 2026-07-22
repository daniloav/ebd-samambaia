package br.com.ice.ebd.model;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "prova")
public class Prova {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "nota_maxima", nullable = false, precision = 5, scale = 2)
    private BigDecimal notaMaxima = new BigDecimal("10.00");

    /** OFFLINE (nota à mão) ou ONLINE (quiz auto-corrigido). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoProva tipo = TipoProva.OFFLINE;

    /** Janela da prova online (opcional). */
    @Column(name = "abre_em")
    private LocalDateTime abreEm;

    @Column(name = "fecha_em")
    private LocalDateTime fechaEm;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "classe_id", nullable = false)
    private Classe classe;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }

    public BigDecimal getNotaMaxima() { return notaMaxima; }
    public void setNotaMaxima(BigDecimal notaMaxima) { this.notaMaxima = notaMaxima; }

    public TipoProva getTipo() { return tipo; }
    public void setTipo(TipoProva tipo) { this.tipo = tipo; }

    public LocalDateTime getAbreEm() { return abreEm; }
    public void setAbreEm(LocalDateTime abreEm) { this.abreEm = abreEm; }

    public LocalDateTime getFechaEm() { return fechaEm; }
    public void setFechaEm(LocalDateTime fechaEm) { this.fechaEm = fechaEm; }

    public Classe getClasse() { return classe; }
    public void setClasse(Classe classe) { this.classe = classe; }
}
