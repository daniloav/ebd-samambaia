package br.com.ice.ebd;

import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.repository.UsuarioRepository;
import br.com.ice.ebd.service.AcessoAlunoService;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AcessoAlunoServiceTest {

    @Inject AcessoAlunoService acesso;
    @Inject UsuarioRepository usuarioRepository;
    @Inject Fixtures fx;

    private Usuario loginDe(Aluno a) {
        return usuarioRepository.find("aluno.id", a.getId()).firstResult();
    }

    @Test
    @TestTransaction
    void alunoGanhaLoginNomeSobrenomeComSenhaPadraoETrocaObrigatoria() {
        Classe c = fx.classe("Turma Acesso");
        Aluno a = fx.aluno("Maria Clara Santos", c, null, true);

        acesso.sincronizarAcesso(a);

        Usuario u = loginDe(a);
        assertNotNull(u, "deveria ter criado um login para o aluno");
        assertEquals("maria.santos", u.getUsername());
        assertTrue(u.isEhAluno());
        assertTrue(u.isPrecisaTrocarSenha(), "deve exigir troca no 1º acesso");
        assertTrue(BcryptUtil.matches(AcessoAlunoService.SENHA_PADRAO, u.getSenhaHash()),
                "a senha padrão deve autenticar");
    }

    @Test
    @TestTransaction
    void loginQueColideGanhaSufixoNumerico() {
        Classe c = fx.classe("Turma Colisao");
        Aluno a1 = fx.aluno("Quintino Zarabatana", c, null, false);
        Aluno a2 = fx.aluno("Quintino Zarabatana", c, null, false);

        acesso.sincronizarAcesso(a1);
        acesso.sincronizarAcesso(a2);

        assertEquals("quintino.zarabatana", loginDe(a1).getUsername());
        assertEquals("quintino.zarabatana2", loginDe(a2).getUsername());
    }

    @Test
    @TestTransaction
    void sincronizarEhIdempotenteEEspelhaAtivo() {
        Classe c = fx.classe("Turma Idem");
        Aluno a = fx.aluno("Pedro Henrique Lima", c, null, false);

        acesso.sincronizarAcesso(a);
        Long idLogin = loginDe(a).getId();

        // desativa o aluno e sincroniza de novo: não duplica o login e espelha o ativo
        a.setAtivo(false);
        acesso.sincronizarAcesso(a);

        Usuario u = loginDe(a);
        assertEquals(idLogin, u.getId(), "não deve criar um segundo login");
        assertTrue(!u.isAtivo(), "login inativo deve espelhar o aluno inativo");
    }

    @Test
    @TestTransaction
    void definirLoginTrocaOUsernameQuandoValido() {
        Classe c = fx.classe("Turma DefLogin");
        Aluno a = fx.aluno("Roberto Carlos Nogueira", c, null, false);
        acesso.sincronizarAcesso(a);

        acesso.definirLogin(a.getId(), "beto.nogueira");

        assertEquals("beto.nogueira", loginDe(a).getUsername());
    }

    @Test
    @TestTransaction
    void definirLoginNormalizaParaMinusculas() {
        Classe c = fx.classe("Turma DefLogin2");
        Aluno a = fx.aluno("Silvia Prado", c, null, false);
        acesso.sincronizarAcesso(a);

        acesso.definirLogin(a.getId(), "  Silvia.Prado  "); // com espaços e maiúsculas

        assertEquals("silvia.prado", loginDe(a).getUsername());
    }

    @Test
    @TestTransaction
    void definirLoginComFormatoInvalidoRejeita() {
        Classe c = fx.classe("Turma DefLogin3");
        Aluno a = fx.aluno("Teste Invalido", c, null, false);
        acesso.sincronizarAcesso(a);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> acesso.definirLogin(a.getId(), "tem espaço"));
        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void definirLoginDuplicadoRejeita() {
        Classe c = fx.classe("Turma DefLogin4");
        Aluno a1 = fx.aluno("Xisto Quaresma", c, null, false);
        Aluno a2 = fx.aluno("Yara Zenteno", c, null, false);
        acesso.sincronizarAcesso(a1);
        acesso.sincronizarAcesso(a2);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> acesso.definirLogin(a2.getId(), loginDe(a1).getUsername()));
        assertEquals(409, ex.getResponse().getStatus());
    }
}
