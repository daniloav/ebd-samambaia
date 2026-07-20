package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Aluno;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class AlunoRepository implements PanacheRepository<Aluno> {

    public List<Aluno> listarOrdenadoPorNome() {
        return listAll(Sort.by("nome"));
    }

    public List<Aluno> listarAtivos() {
        return list("ativo = true order by nome");
    }

    public List<Aluno> listarPorClasse(Long classeId) {
        return list("classe.id = ?1 order by nome", classeId);
    }

    public List<Aluno> listarAtivosPorClasse(Long classeId) {
        return list("classe.id = ?1 and ativo = true order by nome", classeId);
    }

    /**
     * Alunos aptos a receber e-mail (ativos, com opt-in e e-mail preenchido).
     * {@code classeId} nulo = todas as turmas. Usado pelas campanhas.
     */
    public List<Aluno> listarDestinatariosEmail(Long classeId) {
        String base = "ativo = true and recebeNotificacoes = true and email is not null and email <> ''";
        if (classeId == null) {
            return list(base + " order by nome");
        }
        return list(base + " and classe.id = ?1 order by nome", classeId);
    }
}
