package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Submissao;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class SubmissaoRepository implements PanacheRepository<Submissao> {

    /** Tentativa mais recente do aluno na prova, ou {@code null} se ainda não respondeu. */
    public Submissao doAlunoNaProva(Long provaId, Long alunoId) {
        return find("prova.id = ?1 and aluno.id = ?2 order by tentativa desc", provaId, alunoId).firstResult();
    }

    /** Todas as tentativas do aluno na prova, da 1ª à última. */
    public List<Submissao> tentativasDoAluno(Long provaId, Long alunoId) {
        return list("prova.id = ?1 and aluno.id = ?2 order by tentativa", provaId, alunoId);
    }

    public List<Submissao> listarPorProva(Long provaId) {
        return list("prova.id = ?1 order by aluno.id, tentativa", provaId);
    }
}
