package br.com.ice.ebd.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proteção simples de força-bruta no login: após {@link #MAX_TENTATIVAS} falhas
 * seguidas para o mesmo usuário, bloqueia novas tentativas por {@link #BLOQUEIO}.
 * Sucesso zera o contador; falhas antigas ({@link #JANELA}) expiram sozinhas.
 *
 * <p>Estado em memória (suficiente para 1 instância; zera no restart — aceitável,
 * pois o objetivo é frear tentativas automatizadas, não ser um WAF).
 */
@ApplicationScoped
public class ProtecaoLoginService {

    static final int MAX_TENTATIVAS = 5;
    static final Duration BLOQUEIO = Duration.ofSeconds(60);
    static final Duration JANELA = Duration.ofMinutes(15);

    private record Registro(int falhas, Instant ultimaFalha) {}

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();

    /** Lança 429 se o usuário estiver bloqueado por excesso de tentativas. */
    public void verificarBloqueio(String username) {
        Registro r = registros.get(chave(username));
        if (r == null) {
            return;
        }
        if (Duration.between(r.ultimaFalha(), Instant.now()).compareTo(JANELA) > 0) {
            registros.remove(chave(username));
            return;
        }
        if (r.falhas() >= MAX_TENTATIVAS) {
            long restante = BLOQUEIO.minus(Duration.between(r.ultimaFalha(), Instant.now())).toSeconds();
            if (restante > 0) {
                throw new WebApplicationException(
                        "Muitas tentativas de login. Aguarde " + restante + " segundo(s) e tente novamente.",
                        429);
            }
            // bloqueio expirou: dá nova chance mantendo o histórico recente
            registros.put(chave(username), new Registro(MAX_TENTATIVAS - 1, r.ultimaFalha()));
        }
    }

    public void registrarFalha(String username) {
        registros.merge(chave(username), new Registro(1, Instant.now()),
                (antigo, novo) -> new Registro(antigo.falhas() + 1, Instant.now()));
    }

    public void registrarSucesso(String username) {
        registros.remove(chave(username));
    }

    private String chave(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
