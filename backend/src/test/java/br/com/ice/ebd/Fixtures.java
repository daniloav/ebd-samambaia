package br.com.ice.ebd;

import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.DiaSemanaLeitura;
import br.com.ice.ebd.model.TextoBiblicoAula;
import br.com.ice.ebd.model.Presenca;
import br.com.ice.ebd.model.Prova;
import br.com.ice.ebd.model.Role;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.AulaRepository;
import br.com.ice.ebd.repository.ClasseRepository;
import br.com.ice.ebd.repository.PresencaRepository;
import br.com.ice.ebd.repository.ProvaRepository;
import br.com.ice.ebd.repository.TextoBiblicoAulaRepository;
import br.com.ice.ebd.repository.UsuarioRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Cria dados de teste na transação ativa (usar dentro de @TestTransaction). */
@ApplicationScoped
public class Fixtures {

    @Inject ClasseRepository classeRepo;
    @Inject AlunoRepository alunoRepo;
    @Inject AulaRepository aulaRepo;
    @Inject ProvaRepository provaRepo;
    @Inject UsuarioRepository usuarioRepo;
    @Inject PresencaRepository presencaRepo;
    @Inject TextoBiblicoAulaRepository textoRepo;

    public Classe classe(String nome) {
        Classe c = new Classe();
        c.setNome(nome);
        c.setDescricao("teste");
        c.setAtivo(true);
        classeRepo.persist(c);
        return c;
    }

    public Aluno aluno(String nome, Classe classe, String email, boolean optIn) {
        Aluno a = new Aluno();
        a.setNome(nome);
        a.setClasse(classe);
        a.setAtivo(true);
        a.setEmail(email);
        a.setRecebeNotificacoes(optIn);
        alunoRepo.persist(a);
        return a;
    }

    public Aula aula(Classe classe, LocalDate data) {
        Aula aula = new Aula();
        aula.setClasse(classe);
        aula.setData(data);
        aula.setTema("Tema de teste");
        aulaRepo.persist(aula);
        return aula;
    }

    /** Leitura bíblica diária da lição (dia da semana da semana que antecede a aula). */
    public TextoBiblicoAula leitura(Aula aula, DiaSemanaLeitura dia, String referencia) {
        TextoBiblicoAula t = new TextoBiblicoAula();
        t.setDiaSemana(dia);
        t.setReferencia(referencia);
        aula.adicionarTexto(t);
        textoRepo.persist(t);
        return t;
    }

    /** Marca o aluno como presente na aula. */
    public Presenca presente(Aula aula, Aluno aluno) {
        return presenca(aula, aluno, true);
    }

    public Presenca presenca(Aula aula, Aluno aluno, boolean presente) {
        Presenca p = new Presenca();
        p.setAula(aula);
        p.setAluno(aluno);
        p.setPresente(presente);
        presencaRepo.persist(p);
        return p;
    }

    public Prova prova(Classe classe, String notaMaxima) {
        return prova(classe, notaMaxima, LocalDate.now());
    }

    public Prova prova(Classe classe, String notaMaxima, LocalDate data) {
        Prova p = new Prova();
        p.setClasse(classe);
        p.setTitulo("Prova de teste");
        p.setData(data);
        p.setNotaMaxima(new BigDecimal(notaMaxima));
        provaRepo.persist(p);
        return p;
    }

    /** Usuário com role ALUNO vinculado a um aluno (para @TestSecurity com o mesmo username). */
    public Usuario usuarioAluno(String username, Aluno aluno) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setSenhaHash("x");
        u.setEhAluno(true);
        u.setAtivo(true);
        u.setAluno(aluno);
        usuarioRepo.persist(u);
        return u;
    }

    /** Usuário PROFESSOR vinculado a um aluno (o aluno correlato que não conta nas aulas que ele dá). */
    public Usuario usuarioProfessor(String username, Aluno aluno) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setSenhaHash("x");
        u.setEhProfessor(true);
        u.setAtivo(true);
        u.setAluno(aluno);
        usuarioRepo.persist(u);
        return u;
    }

    /** Usuário genérico com papel e e-mail (para testes de tesouraria/roles). */
    public Usuario usuario(String username, Role role, String email) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setSenhaHash("x");
        switch (role) {
            case ADMIN -> u.setEhAdmin(true);
            case PROFESSOR -> u.setEhProfessor(true);
            case ALUNO -> u.setEhAluno(true);
        }
        u.setAtivo(true);
        u.setEmail(email);
        usuarioRepo.persist(u);
        return u;
    }

    /** Usuário com a CAPACIDADE de tesoureiro (role base PROFESSOR + flag). */
    public Usuario tesoureiro(String username, String email) {
        Usuario u = usuario(username, Role.PROFESSOR, email);
        u.setEhTesoureiro(true);
        usuarioRepo.persist(u);
        return u;
    }
}
