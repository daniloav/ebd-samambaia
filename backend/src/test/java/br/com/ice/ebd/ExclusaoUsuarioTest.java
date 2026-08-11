package br.com.ice.ebd;

import br.com.ice.ebd.dto.RequisicaoRequest;
import br.com.ice.ebd.model.Role;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.service.RequisicaoService;
import br.com.ice.ebd.service.UsuarioService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class ExclusaoUsuarioTest {

    @Inject UsuarioService usuarioService;
    @Inject RequisicaoService requisicaoService;
    @Inject Fixtures fx;

    @Test
    @TestSecurity(user = "lid.abriu", roles = "ADMIN")
    @TestTransaction
    void naoExcluiUsuarioQueAbriuRequisicao() {
        Usuario u = fx.usuario("lid.abriu", Role.PROFESSOR, "l@ex.com");
        // a requisição fica com solicitante = usuário logado (lid.abriu)
        requisicaoService.criar(new RequisicaoRequest(
                "Louvor", null, "Cabos", "Motivo", new BigDecimal("10.00"), null, "DINHEIRO", null, null, null, null, null));

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> usuarioService.deletar(u.getId()));
        assertEquals(409, e.getResponse().getStatus(), "quem abriu requisição não pode ser excluído");
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void excluiUsuarioSemRequisicao() {
        Usuario u = fx.usuario("sem.req", Role.PROFESSOR, "s@ex.com");
        assertDoesNotThrow(() -> usuarioService.deletar(u.getId()));
    }
}
