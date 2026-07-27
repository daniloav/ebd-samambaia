package br.com.ice.ebd;

import br.com.ice.ebd.dto.RequisicaoRequest;
import br.com.ice.ebd.dto.RequisicaoResponse;
import br.com.ice.ebd.model.CategoriaAnexo;
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
                new BigDecimal("300.00"), LocalDate.now().plusDays(10), "DINHEIRO", null, null));
        assertTrue(aberta.numero().startsWith("REQ-"), "deve gerar número REQ-...");
        assertEquals("ABERTA", aberta.status());

        RequisicaoResponse aprovada = service.aprovar(aberta.id(),
                new BigDecimal("250.00"), "Aprovado parcial", null);
        assertEquals("APROVADA", aprovada.status());
        assertEquals(0, new BigDecimal("250.00").compareTo(aprovada.valorAprovado()));

        // finalizar sem anexo -> 400
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.finalizar(aberta.id(), new BigDecimal("240.00"), "Comprei", List.of()));
        assertEquals(400, ex.getResponse().getStatus());

        // finalizar com nota -> FINALIZADA
        var anexo = new RequisicaoService.AnexoData("nota.pdf", "application/pdf", "conteudo".getBytes(), CategoriaAnexo.NOTA_FISCAL);
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
                "Infantil", null, "Material", "Aula", new BigDecimal("50.00"), null, null, null, null));
        service.aprovar(a.id(), null, null, null); // valorAprovado = solicitado

        assertEquals(1, cobranca.enviarPendentes()); // 1ª cobrança
        assertEquals(0, cobranca.enviarPendentes()); // mesmo dia -> dedup
    }
    @Test
    @TestSecurity(user = "lider.teste", roles = "ADMIN")
    @TestTransaction
    void pixSoAceitaChaveDoDonoENuncaAleatoria() {
        fx.usuario("lider.teste", Role.ADMIN, "lider@ebd.test");
        // e-mail do próprio solicitante -> OK
        RequisicaoResponse ok = service.criar(new RequisicaoRequest(
                "Louvor", null, "Cabos", "Motivo", new BigDecimal("100.00"), null, "PIX", "EMAIL", "lider@ebd.test"));
        assertEquals("PIX", ok.formaRepasse());
        assertEquals("EMAIL", ok.pixTipo());
        // e-mail de terceiro -> 400
        WebApplicationException e1 = assertThrows(WebApplicationException.class, () -> service.criar(new RequisicaoRequest(
                "Louvor", null, "Cabos", "Motivo", new BigDecimal("100.00"), null, "PIX", "EMAIL", "outro@ex.com")));
        assertEquals(400, e1.getResponse().getStatus());
        // chave aleatória -> 400
        WebApplicationException e2 = assertThrows(WebApplicationException.class, () -> service.criar(new RequisicaoRequest(
                "Louvor", null, "Cabos", "Motivo", new BigDecimal("100.00"), null, "PIX", "ALEATORIA", "abc-123")));
        assertEquals(400, e2.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "tes", roles = "ADMIN")
    @TestTransaction
    void aprovarComComprovanteGuardaAnexoComprovante() {
        fx.usuario("tes", Role.ADMIN, "tes@ebd.test");
        RequisicaoResponse aberta = service.criar(new RequisicaoRequest(
                "Infantil", null, "Material", "Aula", new BigDecimal("50.00"), null, "DINHEIRO", null, null));
        var comprovante = new RequisicaoService.AnexoData("comp.pdf", "application/pdf",
                "x".getBytes(), CategoriaAnexo.COMPROVANTE);
        RequisicaoResponse ap = service.aprovar(aberta.id(), new BigDecimal("50.00"), "ok", comprovante);
        assertEquals("APROVADA", ap.status());
        assertEquals(1, ap.anexos().size());
        assertEquals("COMPROVANTE", ap.anexos().get(0).categoria());
        assertTrue(ap.possuiComprovante(), "detalhe deve marcar possuiComprovante");
        // e a listagem também deve sinalizar (para o líder ver que há comprovante)
        RequisicaoResponse naLista = service.listar(null).stream()
                .filter(x -> x.id().equals(aberta.id())).findFirst().orElseThrow();
        assertTrue(naLista.possuiComprovante(), "a lista deve marcar possuiComprovante");
    }
}
