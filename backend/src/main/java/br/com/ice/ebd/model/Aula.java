package br.com.ice.ebd.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "aula")
public class Aula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate data;

    @Column(length = 200)
    private String tema;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "classe_id", nullable = false)
    private Classe classe;

    /** Professor (usuário PROFESSOR) que deu esta aula; o aluno vinculado a ele não conta na chamada/ranking. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professor_id")
    private Usuario professor;

    /**
     * Aula adiada/cancelada (ex.: evento da igreja no domingo). Uma aula adiada é ignorada por
     * toda pontuação e retrospecto (chamada, rankings, relatórios, boletim, dashboard, frequência,
     * inativação por faltas e promoção de visitante) — ninguém é penalizado por ela.
     */
    @Column(nullable = false)
    private boolean adiada = false;

    /**
     * Último lembrete de chamada pendente enviado ao professor (no dia da aula, de hora em hora
     * a partir das 12h). Serve de dedup: no máximo um lembrete por aula por hora.
     */
    @Column(name = "chamada_cobrada_em")
    private LocalDateTime chamadaCobradaEm;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }

    public String getTema() { return tema; }
    public void setTema(String tema) { this.tema = tema; }

    public Classe getClasse() { return classe; }
    public void setClasse(Classe classe) { this.classe = classe; }

    public Usuario getProfessor() { return professor; }
    public void setProfessor(Usuario professor) { this.professor = professor; }

    public boolean isAdiada() { return adiada; }
    public void setAdiada(boolean adiada) { this.adiada = adiada; }

    public LocalDateTime getChamadaCobradaEm() { return chamadaCobradaEm; }
    public void setChamadaCobradaEm(LocalDateTime chamadaCobradaEm) { this.chamadaCobradaEm = chamadaCobradaEm; }
}
