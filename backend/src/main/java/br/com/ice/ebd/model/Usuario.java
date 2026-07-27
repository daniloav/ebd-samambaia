package br.com.ice.ebd.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(length = 150)
    private String email;

    @Column(nullable = false)
    private boolean ativo = true;

    /** Marca que o usuário deve trocar a senha no próximo acesso (1º login do aluno). */
    @Column(name = "precisa_trocar_senha", nullable = false)
    private boolean precisaTrocarSenha = false;

    /** Papel: administrador (superusuário). */
    @Column(name = "eh_admin", nullable = false)
    private boolean ehAdmin = false;

    /** Papel: professor (superfície de ensino/gestão). */
    @Column(name = "eh_professor", nullable = false)
    private boolean ehProfessor = false;

    /** Papel: aluno (visão própria em /api/me/*). */
    @Column(name = "eh_aluno", nullable = false)
    private boolean ehAluno = false;

    /** Capacidade funcional: pode atuar como tesoureiro (independe da role base). */
    @Column(name = "eh_tesoureiro", nullable = false)
    private boolean ehTesoureiro = false;

    /** Capacidade funcional: pode atuar como líder de ministério (independe da role base). */
    @Column(name = "eh_lider", nullable = false)
    private boolean ehLider = false;

    @Column(name = "data_cadastro", nullable = false)
    private LocalDateTime dataCadastro = LocalDateTime.now();

    /** Aluno vinculado (usado pela role ALUNO para a visão própria). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    /** Turmas que um PROFESSOR pode acessar (RBAC com escopo por classe). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "professor_classe",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "classe_id"))
    private Set<Classe> classes = new LinkedHashSet<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public boolean isPrecisaTrocarSenha() { return precisaTrocarSenha; }
    public void setPrecisaTrocarSenha(boolean precisaTrocarSenha) { this.precisaTrocarSenha = precisaTrocarSenha; }

    public boolean isEhAdmin() { return ehAdmin; }
    public void setEhAdmin(boolean ehAdmin) { this.ehAdmin = ehAdmin; }

    public boolean isEhProfessor() { return ehProfessor; }
    public void setEhProfessor(boolean ehProfessor) { this.ehProfessor = ehProfessor; }

    public boolean isEhAluno() { return ehAluno; }
    public void setEhAluno(boolean ehAluno) { this.ehAluno = ehAluno; }

    public boolean isEhTesoureiro() { return ehTesoureiro; }
    public void setEhTesoureiro(boolean ehTesoureiro) { this.ehTesoureiro = ehTesoureiro; }

    public boolean isEhLider() { return ehLider; }
    public void setEhLider(boolean ehLider) { this.ehLider = ehLider; }

    public LocalDateTime getDataCadastro() { return dataCadastro; }
    public void setDataCadastro(LocalDateTime dataCadastro) { this.dataCadastro = dataCadastro; }

    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }

    public Set<Classe> getClasses() { return classes; }
    public void setClasses(Set<Classe> classes) { this.classes = classes; }
}
