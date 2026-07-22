package br.com.ice.ebd.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Alternativa escolhida pelo aluno para uma questão dentro de uma submissão. */
@Entity
@Table(name = "resposta")
public class Resposta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submissao_id", nullable = false)
    private Submissao submissao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "questao_id", nullable = false)
    private Questao questao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alternativa_id")
    private Alternativa alternativa;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Submissao getSubmissao() { return submissao; }
    public void setSubmissao(Submissao submissao) { this.submissao = submissao; }

    public Questao getQuestao() { return questao; }
    public void setQuestao(Questao questao) { this.questao = questao; }

    public Alternativa getAlternativa() { return alternativa; }
    public void setAlternativa(Alternativa alternativa) { this.alternativa = alternativa; }
}
