package br.com.ice.ebd.repository;

import br.com.ice.ebd.model.Usuario;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class UsuarioRepository implements PanacheRepository<Usuario> {

    public Optional<Usuario> findByUsername(String username) {
        return find("username", username).firstResultOptional();
    }

    public List<Usuario> listarOrdenado() {
        return list("order by username");
    }

    /** IDs das classes vinculadas a um usuário (professor). Consulta direta, sem lazy-loading. */
    public Set<Long> classeIdsDoUsuario(String username) {
        return getEntityManager().createQuery(
                        "select c.id from Usuario u join u.classes c where u.username = :un", Long.class)
                .setParameter("un", username)
                .getResultStream().collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** ID do aluno vinculado a um usuário (role ALUNO), ou {@code null}. */
    public Long alunoIdDoUsuario(String username) {
        return getEntityManager().createQuery(
                        "select u.aluno.id from Usuario u where u.username = :un", Long.class)
                .setParameter("un", username)
                .getResultStream().findFirst().orElse(null);
    }

    /** IDs de alunos que já possuem um login (usuário vinculado). */
    public Set<Long> alunoIdsComUsuario() {
        return getEntityManager().createQuery(
                        "select u.aluno.id from Usuario u where u.aluno.id is not null", Long.class)
                .getResultStream().collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** Mapa aluno.id -> login (username), para exibir o acesso na lista de alunos. */
    public Map<Long, String> loginsPorAluno() {
        Map<Long, String> mapa = new LinkedHashMap<>();
        for (Object[] r : getEntityManager().createQuery(
                        "select u.aluno.id, u.username from Usuario u where u.aluno.id is not null", Object[].class)
                .getResultList()) {
            mapa.put((Long) r[0], (String) r[1]);
        }
        return mapa;
    }

    /** Login (username) do usuário vinculado a um aluno, ou {@code null}. */
    public String loginDoAluno(Long alunoId) {
        return getEntityManager().createQuery(
                        "select u.username from Usuario u where u.aluno.id = :id", String.class)
                .setParameter("id", alunoId)
                .getResultStream().findFirst().orElse(null);
    }

    /** E-mails dos professores ativos (para avisos de novos visitantes). */
    public java.util.List<String> emailsDeProfessoresAtivos() {
        return getEntityManager().createQuery(
                "select u.email from Usuario u where u.role = br.com.ice.ebd.model.Role.PROFESSOR "
                        + "and u.ativo = true and u.email is not null and u.email <> ''", String.class)
                .getResultList();
    }
}
