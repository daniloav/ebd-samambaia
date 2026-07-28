package br.com.ice.ebd.service;

import br.com.ice.ebd.model.ResetSenha;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.repository.ResetSenhaRepository;
import br.com.ice.ebd.repository.UsuarioRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Fluxo "esqueci minha senha/usuário": o usuário informa o e-mail; para cada conta
 * com aquele e-mail geramos um token de uso único (guardamos só o hash) e enviamos
 * um e-mail com o usuário + o link de redefinição. Nunca revelamos se o e-mail existe.
 */
@ApplicationScoped
public class RecuperacaoSenhaService {

    static final int SENHA_MIN = 8;
    private static final SecureRandom RNG = new SecureRandom();

    @Inject UsuarioRepository usuarioRepository;
    @Inject ResetSenhaRepository resetRepository;
    @Inject NotificacaoService notificacao;

    @ConfigProperty(name = "ebd.reset.validade-minutos", defaultValue = "60")
    long validadeMinutos;

    /** Item para o e-mail: o usuário e o token (em claro, só vai no e-mail). */
    public record RecuperacaoItem(String username, String token) {}

    @Transactional
    public void solicitar(String email) {
        if (email == null || email.isBlank()) {
            return; // resposta genérica na camada web
        }
        List<Usuario> contas = usuarioRepository.usuariosAtivosPorEmail(email.trim());
        if (contas.isEmpty()) {
            return; // não revela que o e-mail não existe (anti-enumeração)
        }
        List<RecuperacaoItem> itens = new ArrayList<>();
        for (Usuario u : contas) {
            String token = novoToken();
            ResetSenha r = new ResetSenha();
            r.setUsuario(u);
            r.setTokenHash(sha256(token));
            r.setExpiraEm(LocalDateTime.now().plusMinutes(validadeMinutos));
            resetRepository.persist(r);
            itens.add(new RecuperacaoItem(u.getUsername(), token));
        }
        notificacao.enviarRecuperacaoSenha(email.trim(), itens, validadeMinutos);
    }

    /** Valida o token e devolve o usuário dono (para a tela mostrar de quem é). */
    @Transactional
    public String validar(String token) {
        return tokenValido(token).getUsuario().getUsername();
    }

    @Transactional
    public void redefinir(String token, String novaSenha) {
        if (novaSenha == null || novaSenha.length() < SENHA_MIN) {
            throw bad("A senha deve ter pelo menos " + SENHA_MIN + " caracteres.");
        }
        ResetSenha r = tokenValido(token);
        Usuario u = r.getUsuario();
        u.setSenhaHash(BcryptUtil.bcryptHash(novaSenha));
        u.setPrecisaTrocarSenha(false); // já definiu a própria senha
        r.setUsadoEm(LocalDateTime.now());
    }

    private ResetSenha tokenValido(String token) {
        ResetSenha r = token == null || token.isBlank()
                ? null : resetRepository.findByTokenHash(sha256(token)).orElse(null);
        if (r == null || r.getUsadoEm() != null || r.getExpiraEm().isBefore(LocalDateTime.now())) {
            throw bad("Link inválido ou expirado. Solicite um novo.");
        }
        return r;
    }

    private static String novoToken() {
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte x : d) {
                sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static WebApplicationException bad(String msg) {
        return new WebApplicationException(msg, Response.Status.BAD_REQUEST);
    }
}
