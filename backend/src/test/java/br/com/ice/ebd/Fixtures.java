package br.com.ice.ebd;

import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Prova;
import br.com.ice.ebd.model.Role;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.AulaRepository;
import br.com.ice.ebd.repository.ClasseRepository;
import br.com.ice.ebd.repository.ProvaRepository;
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

    public Prova prova(Classe classe, String notaMaxima) {
        Prova p = new Prova();
        p.setClasse(classe);
        p.setTitulo("Prova de teste");
        p.setData(LocalDate.now());
        p.setNotaMaxima(new BigDecimal(notaMaxima));
        provaRepo.persist(p);
        return p;
    }

    /** Usuário com role ALUNO vinculado a um aluno (para @TestSecurity com o mesmo username). */
    public Usuario usuarioAluno(String username, Aluno aluno) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setSenhaHash("x");
        u.setRole(Role.ALUNO);
        u.setAtivo(true);
        u.setAluno(aluno);
        usuarioRepo.persist(u);
        return u;
    }
}
