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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Uma tentativa de um aluno numa prova respondida pela tela, já com a nota auto-corrigida.
 * Prova ONLINE tem só a tentativa 1; a de RECUPERACAO aceita até 3.
 */
@Entity
@Table(name = "submissao", uniqueConstraints = @UniqueConstraint(
        name = "uq_submissao_tentativa", columnNames = {"prova_id", "aluno_id", "tentativa"}))
public class Submissao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prova_id", nullable = false)
    private Prova prova;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    /** Número da tentativa do aluno nesta prova (1, 2, 3...). */
    @Column(nullable = false)
    private short tentativa = 1;

    @Column(name = "enviada_em", nullable = false)
    private LocalDateTime enviadaEm = LocalDateTime.now();

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal nota;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Prova getProva() { return prova; }
    public void setProva(Prova prova) { this.prova = prova; }

    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }

    public int getTentativa() { return tentativa; }
    public void setTentativa(int tentativa) { this.tentativa = (short) tentativa; }

    public LocalDateTime getEnviadaEm() { return enviadaEm; }
    public void setEnviadaEm(LocalDateTime enviadaEm) { this.enviadaEm = enviadaEm; }

    public BigDecimal getNota() { return nota; }
    public void setNota(BigDecimal nota) { this.nota = nota; }
}
