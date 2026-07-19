package br.com.ice.ebd.security;

import br.com.ice.ebd.model.Usuario;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
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
                .groups(Set.of(usuario.getRole().name()))
                .claim("nome", usuario.getUsername())
                .expiresIn(Duration.ofSeconds(durationSeconds))
                .sign();
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }
}
