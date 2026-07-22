package br.com.ice.ebd;

import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Prova;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.AulaRepository;
import br.com.ice.ebd.repository.ClasseRepository;
import br.com.ice.ebd.repository.ProvaRepository;
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
}
