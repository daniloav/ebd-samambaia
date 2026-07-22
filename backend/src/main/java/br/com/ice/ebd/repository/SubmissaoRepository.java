package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Submissao;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SubmissaoRepository implements PanacheRepository<Submissao> {

    /** Submissão do aluno para a prova, ou {@code null} se ainda não respondeu. */
    public Submissao doAlunoNaProva(Long provaId, Long alunoId) {
        return find("prova.id = ?1 and aluno.id = ?2", provaId, alunoId).firstResult();
    }
}
