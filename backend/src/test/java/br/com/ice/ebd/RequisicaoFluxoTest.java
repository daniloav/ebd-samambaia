package br.com.ice.ebd;

import br.com.ice.ebd.dto.AvaliarRequest;
import br.com.ice.ebd.dto.RequisicaoRequest;
import br.com.ice.ebd.dto.RequisicaoResponse;
import br.com.ice.ebd.model.Role;
import br.com.ice.ebd.service.CobrancaNotaService;
import br.com.ice.ebd.service.RequisicaoService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class RequisicaoFluxoTest {

    @Inject RequisicaoService service;
    @Inject CobrancaNotaService cobranca;
    @Inject Fixtures fx;

    @Test
    @TestSecurity(user = "lider.teste", roles = "ADMIN")
    @TestTransaction
    void fluxoAbrirAprovarFinalizar() {
        fx.usuario("lider.teste", Role.ADMIN, "lider@ebd.test");
        fx.tesoureiro("tes.teste", "tes@ebd.test");

        RequisicaoResponse aberta = service.criar(new RequisicaoRequest(
                "Louvor", "Culto de Natal", "Compra de cordas", "Instrumento quebrou",
                new BigDecimal("300.00"), LocalDate.now().plusDays(10)));
        assertTrue(aberta.numero().startsWith("REQ-"), "deve gerar número REQ-...");
        assertEquals("ABERTA", aberta.status());

        RequisicaoResponse aprovada = service.aprovar(aberta.id(),
                new AvaliarRequest(new BigDecimal("250.00"), "Aprovado parcial"));
        assertEquals("APROVADA", aprovada.status());
        assertEquals(0, new BigDecimal("250.00").compareTo(aprovada.valorAprovado()));

        // finalizar sem anexo -> 400
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.finalizar(aberta.id(), new BigDecimal("240.00"), "Comprei", List.of()));
        assertEquals(400, ex.getResponse().getStatus());

        // finalizar com nota -> FINALIZADA
        var anexo = new RequisicaoService.AnexoData("nota.pdf", "application/pdf", "conteudo".getBytes());
        RequisicaoResponse fim = service.finalizar(aberta.id(), new BigDecimal("240.00"), "Comprei", List.of(anexo));
        assertEquals("FINALIZADA", fim.status());
        assertEquals(1, fim.anexos().size());
    }

    @Test
    @TestSecurity(user = "lider2", roles = "ADMIN")
    @TestTransaction
    void cobrancaDeNotaNaoRepeteNoMesmoDia() {
        fx.usuario("lider2", Role.ADMIN, "lider2@ebd.test");
        RequisicaoResponse a = service.criar(new RequisicaoRequest(
                "Infantil", null, "Material", "Aula", new BigDecimal("50.00"), null));
        service.aprovar(a.id(), new AvaliarRequest(null, null)); // valorAprovado = solicitado

        assertEquals(1, cobranca.enviarPendentes()); // 1ª cobrança
        assertEquals(0, cobranca.enviarPendentes()); // mesmo dia -> dedup
    }
}
