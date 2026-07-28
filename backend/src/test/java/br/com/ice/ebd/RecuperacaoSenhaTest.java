package br.com.ice.ebd;

import br.com.ice.ebd.model.ResetSenha;
import br.com.ice.ebd.model.Role;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.repository.ResetSenhaRepository;
import br.com.ice.ebd.service.RecuperacaoSenhaService;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class RecuperacaoSenhaTest {

    @Inject RecuperacaoSenhaService service;
    @Inject ResetSenhaRepository resetRepo;
    @Inject Fixtures fx;

    private static String sha256(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte x : d) { sb.append(String.format("%02x", x)); }
        return sb.toString();
    }

    private ResetSenha criarToken(Usuario u, String token, LocalDateTime expira) throws Exception {
        ResetSenha r = new ResetSenha();
        r.setUsuario(u);
        r.setTokenHash(sha256(token));
        r.setExpiraEm(expira);
        resetRepo.persist(r);
        return r;
    }

    @Test
    @TestTransaction
    void solicitarCriaTokenSoParaEmailExistente() {
        fx.usuario("maria", Role.PROFESSOR, "maria@ex.com");
        service.solicitar("maria@ex.com");
        assertEquals(1, resetRepo.count(), "deve criar 1 token para o e-mail existente");

        service.solicitar("naoexiste@ex.com");
        assertEquals(1, resetRepo.count(), "e-mail inexistente não cria token (anti-enumeração)");
    }

    @Test
    @TestTransaction
    void redefinirTrocaSenhaEhDeUsoUnico() throws Exception {
        Usuario u = fx.usuario("joao", Role.ALUNO, "joao@ex.com");
        ResetSenha r = criarToken(u, "tok-abc", LocalDateTime.now().plusHours(1));

        assertEquals("joao", service.validar("tok-abc"));
        service.redefinir("tok-abc", "SenhaForte123");

        assertTrue(BcryptUtil.matches("SenhaForte123", u.getSenhaHash()), "a senha deve ter mudado");
        assertNotNull(resetRepo.findById(r.getId()).getUsadoEm(), "token deve ficar marcado como usado");
        assertThrows(WebApplicationException.class,
                () -> service.redefinir("tok-abc", "OutraSenha123"), "token usado não pode repetir");
    }

    @Test
    @TestTransaction
    void tokenExpiradoOuSenhaFracaOuInvalidoFalham() throws Exception {
        Usuario u = fx.usuario("ana", Role.ALUNO, "ana@ex.com");
        criarToken(u, "tok-exp", LocalDateTime.now().minusMinutes(1)); // já expirado
        assertThrows(WebApplicationException.class, () -> service.redefinir("tok-exp", "SenhaForte123"));

        criarToken(u, "tok-ok", LocalDateTime.now().plusHours(1));
        assertThrows(WebApplicationException.class, () -> service.redefinir("tok-ok", "123")); // senha fraca
        assertThrows(WebApplicationException.class, () -> service.validar("nao-existe")); // token inválido

        // senha fraca não altera o hash (fixture usa "x" como placeholder)
        assertEquals("x", u.getSenhaHash());
    }
}
