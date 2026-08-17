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
import java.time.LocalDateTime;

/**
 * Um episódio de inativação de aluno: aberto quando ele fica inativo e fechado
 * ({@link #reativadoEm}) quando volta a ficar ativo. Quem sai e volta várias vezes tem
 * várias linhas — é desta tabela que sai o relatório de alunos inativados.
 */
@Entity
@Table(name = "aluno_inativacao")
public class AlunoInativacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    /** Nulo só no histórico importado pela V30 (inativações anteriores ao registro). */
    @Column(name = "inativado_em")
    private LocalDateTime inativadoEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MotivoInativacao motivo;

    /** Quantas faltas seguidas fecharam a sequência (só na inativação automática). */
    @Column(name = "faltas_seguidas")
    private Integer faltasSeguidas;

    @Column(name = "inativado_por", length = 60)
    private String inativadoPor;

    @Column(name = "reativado_em")
    private LocalDateTime reativadoEm;

    @Column(name = "reativado_por", length = 60)
    private String reativadoPor;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }

    public LocalDateTime getInativadoEm() { return inativadoEm; }
    public void setInativadoEm(LocalDateTime inativadoEm) { this.inativadoEm = inativadoEm; }

    public MotivoInativacao getMotivo() { return motivo; }
    public void setMotivo(MotivoInativacao motivo) { this.motivo = motivo; }

    public Integer getFaltasSeguidas() { return faltasSeguidas; }
    public void setFaltasSeguidas(Integer faltasSeguidas) { this.faltasSeguidas = faltasSeguidas; }

    public String getInativadoPor() { return inativadoPor; }
    public void setInativadoPor(String inativadoPor) { this.inativadoPor = inativadoPor; }

    public LocalDateTime getReativadoEm() { return reativadoEm; }
    public void setReativadoEm(LocalDateTime reativadoEm) { this.reativadoEm = reativadoEm; }

    public String getReativadoPor() { return reativadoPor; }
    public void setReativadoPor(String reativadoPor) { this.reativadoPor = reativadoPor; }
}
