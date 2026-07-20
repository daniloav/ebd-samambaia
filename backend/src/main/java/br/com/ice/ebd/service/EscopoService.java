package br.com.ice.ebd.service;

import br.com.ice.ebd.repository.UsuarioRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import java.util.Set;

/**
 * Resolve o escopo de autorização do usuário logado (RBAC com escopo por classe).
 *
 * <ul>
 *   <li><b>ADMIN</b> — acesso a tudo ({@link #classesPermitidas()} devolve {@code null} = todas).</li>
 *   <li><b>PROFESSOR</b> — só as turmas vinculadas a ele (tabela {@code professor_classe}).</li>
 *   <li><b>ALUNO</b> — só o próprio cadastro (via {@link #alunoIdLogado()}).</li>
 * </ul>
 */
@ApplicationScoped
public class EscopoService {

    @Inject SecurityIdentity identity;
    @Inject UsuarioRepository usuarioRepository;

    public boolean isAdmin() { return identity.hasRole("ADMIN"); }
    public boolean isProfessor() { return identity.hasRole("PROFESSOR"); }
    public boolean isAluno() { return identity.hasRole("ALUNO"); }

    private String username() { return identity.getPrincipal().getName(); }

    /** Conjunto de IDs de classe que o usuário pode acessar; {@code null} = todas (ADMIN). */
    public Set<Long> classesPermitidas() {
        if (isAdmin()) {
            return null;
        }
        return usuarioRepository.classeIdsDoUsuario(username());
    }

    /**
     * Garante que o usuário pode acessar a turma informada. ADMIN sempre pode.
     * Para não-admin, exige uma turma explícita e que ela esteja no escopo dele.
     */
    public void assertClasse(Long classeId) {
        if (isAdmin()) {
            return;
        }
        Set<Long> permitidas = classesPermitidas();
        if (classeId == null || permitidas == null || !permitidas.contains(classeId)) {
            throw new ForbiddenException("Você não tem acesso a esta turma.");
        }
    }

    /** ID do aluno vinculado ao usuário logado (role ALUNO), ou {@code null}. */
    public Long alunoIdLogado() {
        return usuarioRepository.alunoIdDoUsuario(username());
    }
}
