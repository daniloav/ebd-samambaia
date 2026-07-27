package br.com.ice.ebd.security;

import br.com.ice.ebd.model.Usuario;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Emite tokens JWT assinados para usuários autenticados. */
@ApplicationScoped
public class TokenService {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "ebd.jwt.duration-seconds", defaultValue = "28800")
    long durationSeconds;

    public String gerarToken(Usuario usuario) {
        return Jwt.issuer(issuer)
                .upn(usuario.getUsername())
                .subject(String.valueOf(usuario.getId()))
                .groups(gruposDe(usuario))
                .claim("nome", usuario.getUsername())
                .expiresIn(Duration.ofSeconds(durationSeconds))
                .sign();
    }

    /**
     * Grupos do JWT = role base + capacidades funcionais. ADMIN recebe todas as
     * capacidades por padrão (acesso a tudo). Assim os @RolesAllowed("TESOUREIRO"/
     * "LIDER") continuam valendo sem depender da role base.
     */
    private Set<String> gruposDe(Usuario u) {
        Set<String> grupos = new HashSet<>();
        if (u.isEhAdmin())     grupos.add("ADMIN");
        if (u.isEhProfessor()) grupos.add("PROFESSOR");
        if (u.isEhAluno())     grupos.add("ALUNO");
        boolean admin = u.isEhAdmin();
        if (admin || u.isEhTesoureiro()) grupos.add("TESOUREIRO");
        if (admin || u.isEhLider())      grupos.add("LIDER");
        return grupos;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }
}
