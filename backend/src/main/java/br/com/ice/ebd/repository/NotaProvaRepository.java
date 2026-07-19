package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.NotaProva;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class NotaProvaRepository implements PanacheRepository<NotaProva> {

    public List<NotaProva> listarPorProva(Long provaId) {
        return list("prova.id", provaId);
    }

    public List<NotaProva> listarPorAluno(Long alunoId) {
        return list("aluno.id", alunoId);
    }
}
