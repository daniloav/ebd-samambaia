package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Presenca;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class PresencaRepository implements PanacheRepository<Presenca> {

    public List<Presenca> listarPorAula(Long aulaId) {
        return list("aula.id", aulaId);
    }

    public List<Presenca> listarPorAluno(Long alunoId) {
        return list("aluno.id", alunoId);
    }

    public void deletarPorAula(Long aulaId) {
        delete("aula.id", aulaId);
    }
}
