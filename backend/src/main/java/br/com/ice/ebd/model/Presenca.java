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

@Entity
@Table(name = "presenca", uniqueConstraints = @UniqueConstraint(
        name = "uq_presenca_aula_aluno", columnNames = {"aula_id", "aluno_id"}))
public class Presenca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aula_id", nullable = false)
    private Aula aula;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    @Column(nullable = false)
    private boolean presente = false;

    @Column(name = "trouxe_biblia", nullable = false)
    private boolean trouxeBiblia = false;

    @Column(name = "trouxe_revista", nullable = false)
    private boolean trouxeRevista = false;

    @Column(name = "estudou_licao", nullable = false)
    private boolean estudouLicao = false;

    /** Assinatura do estado já notificado por e-mail (presente+itens); null = nunca notificado. */
    @jakarta.persistence.Column(name = "notificada_assinatura", length = 16)
    private String notificadaAssinatura;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Aula getAula() { return aula; }
    public void setAula(Aula aula) { this.aula = aula; }

    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }

    public boolean isPresente() { return presente; }
    public void setPresente(boolean presente) { this.presente = presente; }

    public boolean isTrouxeBiblia() { return trouxeBiblia; }
    public void setTrouxeBiblia(boolean trouxeBiblia) { this.trouxeBiblia = trouxeBiblia; }

    public boolean isTrouxeRevista() { return trouxeRevista; }
    public void setTrouxeRevista(boolean trouxeRevista) { this.trouxeRevista = trouxeRevista; }

    public boolean isEstudouLicao() { return estudouLicao; }
    public void setEstudouLicao(boolean estudouLicao) { this.estudouLicao = estudouLicao; }

    public String getNotificadaAssinatura() { return notificadaAssinatura; }
    public void setNotificadaAssinatura(String notificadaAssinatura) { this.notificadaAssinatura = notificadaAssinatura; }

}
