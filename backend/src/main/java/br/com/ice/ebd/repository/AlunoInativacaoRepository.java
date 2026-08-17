package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.AlunoInativacao;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class AlunoInativacaoRepository implements PanacheRepository<AlunoInativacao> {

    /** Episódio ainda aberto do aluno (ele saiu e não voltou), se houver. */
    public Optional<AlunoInativacao> abertoDoAluno(Long alunoId) {
        return find("aluno.id = ?1 and reativadoEm is null order by id desc", alunoId).firstResultOptional();
    }
}
