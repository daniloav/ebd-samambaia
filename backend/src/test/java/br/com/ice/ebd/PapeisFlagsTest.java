package br.com.ice.ebd;

import br.com.ice.ebd.model.Role;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.security.TokenService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Papéis são capacidades (flags): um usuário pode acumular professor + aluno no mesmo login. */
@QuarkusTest
class PapeisFlagsTest {

    @Inject TokenService tokenService;
    @Inject Fixtures fx;

    /** Lê o claim "groups" do payload do JWT (sem verificar assinatura — basta o conteúdo). */
    private JsonArray gruposDoToken(String token) {
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        return new JsonObject(payload).getJsonArray("groups");
    }

    @Test
    @TestTransaction
    void professorEAlunoRecebeOsDoisGrupos() {
        Usuario u = fx.usuario("prof.aluno", Role.PROFESSOR, "pa@ebd.test");
        u.setEhAluno(true); // agora é professor E aluno no mesmo login
        JsonArray g = gruposDoToken(tokenService.gerarToken(u));
        assertTrue(g.contains("PROFESSOR"), "deve ter o grupo PROFESSOR");
        assertTrue(g.contains("ALUNO"), "deve ter o grupo ALUNO");
    }

    @Test
    @TestTransaction
    void alunoPuroNaoRecebeProfessor() {
        Usuario u = fx.usuario("so.aluno", Role.ALUNO, "sa@ebd.test");
        JsonArray g = gruposDoToken(tokenService.gerarToken(u));
        assertTrue(g.contains("ALUNO"));
        assertFalse(g.contains("PROFESSOR"), "aluno puro não pode virar professor");
    }

    @Test
    @TestTransaction
    void adminRecebeTudo() {
        Usuario u = fx.usuario("admin.tudo", Role.ADMIN, "at@ebd.test");
        JsonArray g = gruposDoToken(tokenService.gerarToken(u));
        assertTrue(g.contains("ADMIN"));
        assertTrue(g.contains("TESOUREIRO"), "admin recebe tesouraria por padrão");
        assertTrue(g.contains("LIDER"), "admin recebe líder por padrão");
    }
}
