package br.com.ice.ebd;

import br.com.ice.ebd.dto.RequisicaoRequest;
import br.com.ice.ebd.dto.RequisicaoResponse;
import br.com.ice.ebd.model.CategoriaAnexo;
import br.com.ice.ebd.model.Role;
import br.com.ice.ebd.repository.RequisicaoRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class RequisicaoFluxoTest {

    @Inject RequisicaoService service;
    @Inject CobrancaNotaService cobranca;
    @Inject RequisicaoRepository repository;
    @Inject Fixtures fx;

    @Test
    @TestSecurity(user = "lider.teste", roles = "ADMIN")
    @TestTransaction
    void fluxoAbrirAprovarFinalizar() {
        fx.usuario("lider.teste", Role.ADMIN, "lider@ebd.test");
        fx.tesoureiro("tes.teste", "tes@ebd.test");

        RequisicaoResponse aberta = service.criar(new RequisicaoRequest(
                "Louvor", "Culto de Natal", "Compra de cordas", "Instrumento quebrou",
                new BigDecimal("300.00"), LocalDate.now().plusDays(10), "DINHEIRO", null, null, null, null, null));
        assertTrue(aberta.numero().startsWith("REQ-"), "deve gerar número REQ-...");
        assertEquals("ABERTA", aberta.status());

        RequisicaoResponse aprovada = service.aprovar(aberta.id(),
                new BigDecimal("250.00"), "Aprovado parcial", null);
        assertEquals("APROVADA", aprovada.status());
        assertEquals(0, new BigDecimal("250.00").compareTo(aprovada.valorAprovado()));

        // finalizar sem anexo -> 400
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.finalizar(aberta.id(), new BigDecimal("250.00"), "Comprei", List.of(), null));
        assertEquals(400, ex.getResponse().getStatus());

        // finalizar com nota, gastando todo o aprovado (sem troco) -> FINALIZADA
        var anexo = new RequisicaoService.AnexoData("nota.pdf", "application/pdf", "conteudo".getBytes(), CategoriaAnexo.NOTA_FISCAL);
        RequisicaoResponse fim = service.finalizar(aberta.id(), new BigDecimal("250.00"), "Comprei", List.of(anexo), null);
        assertEquals("FINALIZADA", fim.status());
        assertEquals(1, fim.anexos().size());
    }

    @Test
    @TestSecurity(user = "lider2", roles = "ADMIN")
    @TestTransaction
    void cobrancaDeNotaNaoRepeteNoMesmoDia() {
        fx.usuario("lider2", Role.ADMIN, "lider2@ebd.test");
        RequisicaoResponse a = service.criar(new RequisicaoRequest(
                "Infantil", null, "Material", "Aula", new BigDecimal("50.00"), null, null, null, null, null, null, null));
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
                "Louvor", null, "Cabos", "Motivo", new BigDecimal("100.00"), null, "PIX", "EMAIL", "lider@ebd.test", null, null, null));
        assertEquals("PIX", ok.formaRepasse());
        assertEquals("EMAIL", ok.pixTipo());
        // e-mail de terceiro -> 400
        WebApplicationException e1 = assertThrows(WebApplicationException.class, () -> service.criar(new RequisicaoRequest(
                "Louvor", null, "Cabos", "Motivo", new BigDecimal("100.00"), null, "PIX", "EMAIL", "outro@ex.com", null, null, null)));
        assertEquals(400, e1.getResponse().getStatus());
        // chave aleatória -> 400
        WebApplicationException e2 = assertThrows(WebApplicationException.class, () -> service.criar(new RequisicaoRequest(
                "Louvor", null, "Cabos", "Motivo", new BigDecimal("100.00"), null, "PIX", "ALEATORIA", "abc-123", null, null, null)));
        assertEquals(400, e2.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "tes", roles = "ADMIN")
    @TestTransaction
    void aprovarComComprovanteGuardaAnexoComprovante() {
        fx.usuario("tes", Role.ADMIN, "tes@ebd.test");
        RequisicaoResponse aberta = service.criar(new RequisicaoRequest(
                "Infantil", null, "Material", "Aula", new BigDecimal("50.00"), null, "DINHEIRO", null, null, null, null, null));
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

    @Test
    @TestSecurity(user = "lider.troco", roles = "ADMIN")
    @TestTransaction
    void finalizarComTrocoExigeComprovanteDeDevolucao() {
        fx.usuario("lider.troco", Role.ADMIN, "lider.troco@ebd.test");
        RequisicaoResponse aberta = service.criar(new RequisicaoRequest(
                "Louvor", null, "Cabos", "Motivo", new BigDecimal("300.00"), null, "DINHEIRO", null, null, null, null, null));
        service.aprovar(aberta.id(), new BigDecimal("250.00"), "ok", null); // aprovado 250

        var nota = new RequisicaoService.AnexoData("nota.pdf", "application/pdf", "n".getBytes(), CategoriaAnexo.NOTA_FISCAL);
        // gastou 200 -> troco de 50, sem comprovante do troco -> 400
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.finalizar(aberta.id(), new BigDecimal("200.00"), "Comprei", List.of(nota), null));
        assertEquals(400, ex.getResponse().getStatus());
        assertTrue(ex.getMessage().contains("troco"), "erro deve mencionar o troco");

        // agora com o comprovante da devolução do troco -> FINALIZADA (2 anexos)
        var troco = new RequisicaoService.AnexoData("troco.pdf", "application/pdf", "t".getBytes(), CategoriaAnexo.TROCO);
        RequisicaoResponse fim = service.finalizar(aberta.id(), new BigDecimal("200.00"), "Comprei", List.of(nota), troco);
        assertEquals("FINALIZADA", fim.status());
        assertEquals(2, fim.anexos().size());
        assertTrue(fim.anexos().stream().anyMatch(a -> a.categoria().equals("TROCO")),
                "deve guardar o comprovante do troco");
    }

    @Test
    @TestSecurity(user = "lider.oferta", roles = "ADMIN")
    @TestTransaction
    void pixDeTerceiroExigeNomeDoBeneficiarioEDispensaOwnership() {
        fx.usuario("lider.oferta", Role.ADMIN, "lider.oferta@ebd.test");
        // chave de outra pessoa, declarada como TERCEIRO + nome -> OK (oferta de amor)
        RequisicaoResponse ok = service.criar(new RequisicaoRequest(
                "Ação Social", null, "Oferta de amor", "Irmão desempregado", new BigDecimal("400.00"), null,
                "PIX", "EMAIL", "irmao@ex.com", "TERCEIRO", "Irmão Beneficiado", "Membro em necessidade"));
        assertEquals("TERCEIRO", ok.pixTitular());
        assertEquals("Irmão Beneficiado", ok.pixBeneficiarioNome());
        assertEquals("irmao@ex.com", ok.pixChave());

        // TERCEIRO sem o nome do beneficiário -> 400
        WebApplicationException semNome = assertThrows(WebApplicationException.class, () -> service.criar(new RequisicaoRequest(
                "Ação Social", null, "Oferta de amor", "Motivo", new BigDecimal("400.00"), null,
                "PIX", "EMAIL", "irmao@ex.com", "TERCEIRO", "  ", null)));
        assertEquals(400, semNome.getResponse().getStatus());

        // formato da chave continua valendo (CPF precisa dos 11 dígitos) e aleatória segue proibida
        WebApplicationException cpfRuim = assertThrows(WebApplicationException.class, () -> service.criar(new RequisicaoRequest(
                "Ação Social", null, "Oferta de amor", "Motivo", new BigDecimal("400.00"), null,
                "PIX", "CPF", "123", "TERCEIRO", "Irmão Beneficiado", null)));
        assertEquals(400, cpfRuim.getResponse().getStatus());
        WebApplicationException aleatoria = assertThrows(WebApplicationException.class, () -> service.criar(new RequisicaoRequest(
                "Ação Social", null, "Oferta de amor", "Motivo", new BigDecimal("400.00"), null,
                "PIX", "ALEATORIA", "abc-123", "TERCEIRO", "Irmão Beneficiado", null)));
        assertEquals(400, aleatoria.getResponse().getStatus());
    }

    @Test
    @TestSecurity(user = "lider.oferta2", roles = "ADMIN")
    @TestTransaction
    void ofertaDeAmorFinalizaComComprovanteNoLugarDaNotaFiscal() {
        fx.usuario("lider.oferta2", Role.ADMIN, "lider.oferta2@ebd.test");
        RequisicaoResponse aberta = service.criar(new RequisicaoRequest(
                "Ação Social", null, "Oferta de amor", "Irmã doente", new BigDecimal("200.00"), null,
                "PIX", "TELEFONE", "(61) 99999-1234", "TERCEIRO", "Irmã Beneficiada", null));
        service.aprovar(aberta.id(), new BigDecimal("200.00"), "ok", null);

        // sem nenhum comprovante (nem do tesoureiro, nem agora) -> 400
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> service.finalizar(aberta.id(), new BigDecimal("200.00"), null, List.of(), null));
        assertEquals(400, ex.getResponse().getStatus());

        // o líder anexa o comprovante da transferência -> guardado como COMPROVANTE, FINALIZADA
        var comp = new RequisicaoService.AnexoData("pix.pdf", "application/pdf", "c".getBytes(), CategoriaAnexo.NOTA_FISCAL);
        RequisicaoResponse fim = service.finalizar(aberta.id(), new BigDecimal("200.00"), "Transferido", List.of(comp), null);
        assertEquals("FINALIZADA", fim.status());
        assertEquals("COMPROVANTE", fim.anexos().get(0).categoria());
    }

    /**
     * O sequencial do número vem do maior já usado, não da contagem: apagar uma requisição
     * (limpeza de dados de teste em produção) não pode fazer a próxima reemitir um número
     * existente — a unique de {@code numero} estouraria e a abertura pararia de funcionar.
     */
    @Test
    @TestSecurity(user = "lider.numero", roles = "ADMIN")
    @TestTransaction
    void numeroNaoSeRepeteDepoisDeApagarUmaRequisicao() {
        fx.usuario("lider.numero", Role.ADMIN, "lider.numero@ebd.test");
        RequisicaoResponse primeira = service.criar(new RequisicaoRequest(
                "Louvor", null, "Cabos", "Motivo", new BigDecimal("10.00"), null, null, null, null, null, null, null));
        RequisicaoResponse segunda = service.criar(new RequisicaoRequest(
                "Louvor", null, "Cabos", "Motivo", new BigDecimal("10.00"), null, null, null, null, null, null, null));
        assertEquals(sequencia(primeira) + 1, sequencia(segunda));

        repository.deleteById(primeira.id());

        RequisicaoResponse terceira = service.criar(new RequisicaoRequest(
                "Louvor", null, "Cabos", "Motivo", new BigDecimal("10.00"), null, null, null, null, null, null, null));
        assertNotEquals(segunda.numero(), terceira.numero());
        assertEquals(sequencia(segunda) + 1, sequencia(terceira));
    }

    /**
     * Duas requisições da mesma compra viram uma só: a principal passa a valer a soma (o valor
     * redondo que o tesoureiro vai repassar) e a outra sai de cena com status JUNTADA. Como a
     * absorvida guarda o próprio valor, desfazer devolve exatamente o que entrou.
     */
    @Test
    @TestSecurity(user = "lider.junta", roles = "ADMIN")
    @TestTransaction
    void juntarSomaOsValoresEDesfazerDevolveCadaUm() {
        fx.usuario("lider.junta", Role.ADMIN, "lider.junta@ebd.test");
        RequisicaoResponse a = service.criar(new RequisicaoRequest(
                "Louvor", null, "Cordas", "Instrumento", new BigDecimal("120.35"), null,
                "DINHEIRO", null, null, null, null, null));
        RequisicaoResponse b = service.criar(new RequisicaoRequest(
                "Louvor", null, "Palhetas", "Mesma compra", new BigDecimal("79.65"), null,
                "DINHEIRO", null, null, null, null, null));

        RequisicaoResponse principal = service.juntar(a.id(), List.of(b.id()));
        assertEquals(0, new BigDecimal("200.00").compareTo(principal.valorSolicitado()), "valor vira a soma");
        assertEquals(1, principal.juntadas().size());
        assertEquals(b.numero(), principal.juntadas().get(0).numero());

        RequisicaoResponse absorvida = service.buscar(b.id());
        assertEquals("JUNTADA", absorvida.status());
        assertEquals(a.numero(), absorvida.juntadaNaNumero());
        assertEquals(0, new BigDecimal("79.65").compareTo(absorvida.valorSolicitado()), "absorvida guarda o próprio valor");

        // cancelar a principal deixaria a absorvida órfã -> bloqueado
        WebApplicationException cancelar = assertThrows(WebApplicationException.class, () -> service.cancelar(a.id()));
        assertEquals(400, cancelar.getResponse().getStatus());

        RequisicaoResponse separada = service.separar(a.id());
        assertEquals(0, new BigDecimal("120.35").compareTo(separada.valorSolicitado()), "principal devolve o que entrou");
        assertTrue(separada.juntadas().isEmpty());
        assertEquals("ABERTA", service.buscar(b.id()).status());
    }

    /**
     * Juntar só vale para pedidos ainda em aberto e que o tesoureiro possa pagar de uma vez:
     * mesma forma de repasse (e, no PIX, a mesma chave).
     */
    @Test
    @TestSecurity(user = "lider.junta2", roles = "ADMIN")
    @TestTransaction
    void naoJuntaAvaliadaNemComRepasseDiferente() {
        fx.usuario("lider.junta2", Role.ADMIN, "lider.junta2@ebd.test");
        RequisicaoResponse dinheiro = service.criar(new RequisicaoRequest(
                "Infantil", null, "Material", "Aula", new BigDecimal("50.00"), null,
                "DINHEIRO", null, null, null, null, null));
        RequisicaoResponse pix = service.criar(new RequisicaoRequest(
                "Infantil", null, "Lanche", "Aula", new BigDecimal("30.00"), null,
                "PIX", "EMAIL", "lider.junta2@ebd.test", null, null, null));

        // formas de repasse diferentes -> 400
        WebApplicationException formas = assertThrows(WebApplicationException.class,
                () -> service.juntar(dinheiro.id(), List.of(pix.id())));
        assertEquals(400, formas.getResponse().getStatus());

        // já avaliada -> 400 (o tesoureiro decidiu sobre o valor de cada uma)
        RequisicaoResponse outra = service.criar(new RequisicaoRequest(
                "Infantil", null, "Material 2", "Aula", new BigDecimal("20.00"), null,
                "DINHEIRO", null, null, null, null, null));
        service.aprovar(outra.id(), null, null, null);
        WebApplicationException avaliada = assertThrows(WebApplicationException.class,
                () -> service.juntar(dinheiro.id(), List.of(outra.id())));
        assertEquals(400, avaliada.getResponse().getStatus());

        // principal já aprovada + alvo ainda em aberto = estágios diferentes -> 400
        service.aprovar(dinheiro.id(), null, null, null);
        WebApplicationException estagios = assertThrows(WebApplicationException.class,
                () -> service.juntar(dinheiro.id(), List.of(pix.id())));
        assertEquals(400, estagios.getResponse().getStatus());
    }

    /**
     * Duas requisições já aprovadas também se juntam: o líder recebeu dois repasses da mesma
     * compra e vai prestar contas com uma nota só. Aí a soma é do <b>valor aprovado</b>, que é o
     * que a nota precisa cobrir (e a base do troco).
     */
    @Test
    @TestSecurity(user = "lider.aprov", roles = "ADMIN")
    @TestTransaction
    void juntarAprovadasSomaOValorAprovadoEDesfazerDevolve() {
        fx.usuario("lider.aprov", Role.ADMIN, "lider.aprov@ebd.test");
        RequisicaoResponse a = service.criar(new RequisicaoRequest(
                "Diaconia", null, "Cesta básica", "Família", new BigDecimal("150.00"), null,
                "DINHEIRO", null, null, null, null, null));
        RequisicaoResponse b = service.criar(new RequisicaoRequest(
                "Diaconia", null, "Leite e pão", "Mesma compra", new BigDecimal("60.00"), null,
                "DINHEIRO", null, null, null, null, null));
        service.aprovar(a.id(), new BigDecimal("140.00"), "ok", null);
        service.aprovar(b.id(), new BigDecimal("60.00"), "ok", null);

        RequisicaoResponse principal = service.juntar(a.id(), List.of(b.id()));
        assertEquals("APROVADA", principal.status());
        assertEquals(0, new BigDecimal("200.00").compareTo(principal.valorAprovado()), "aprovado vira a soma");
        assertEquals(0, new BigDecimal("210.00").compareTo(principal.valorSolicitado()), "solicitado também soma");
        assertTrue(principal.podeSeparar(), "ainda dá para desfazer");

        RequisicaoResponse absorvida = service.buscar(b.id());
        assertEquals("JUNTADA", absorvida.status());
        assertEquals(0, new BigDecimal("60.00").compareTo(absorvida.valorAprovado()), "absorvida guarda o próprio aprovado");

        // uma nota fiscal só presta contas dos 200 juntos
        var nota = new RequisicaoService.AnexoData("nota.pdf", "application/pdf", "n".getBytes(), CategoriaAnexo.NOTA_FISCAL);
        RequisicaoResponse separada = service.separar(a.id());
        assertEquals(0, new BigDecimal("140.00").compareTo(separada.valorAprovado()), "principal devolve o aprovado");
        assertEquals("APROVADA", service.buscar(b.id()).status(), "absorvida volta ao estágio de antes");

        RequisicaoResponse dnv = service.juntar(a.id(), List.of(b.id()));
        RequisicaoResponse fim = service.finalizar(dnv.id(), new BigDecimal("200.00"), "Comprei tudo", List.of(nota), null);
        assertEquals("FINALIZADA", fim.status());
    }

    /**
     * Juntar uma aberta a uma aprovada faria o valor já liberado ficar menor que a soma, sem nada
     * avisar o tesoureiro — e, depois que ele avalia uma junção de abertas, separar deixaria as
     * partes sem cobertura, porque o aprovado nasceu somado.
     */
    @Test
    @TestSecurity(user = "lider.estagio", roles = "ADMIN")
    @TestTransaction
    void estagiosDiferentesNaoJuntamESepararTravaDepoisDaAvaliacao() {
        fx.usuario("lider.estagio", Role.ADMIN, "lider.estagio@ebd.test");
        RequisicaoResponse aberta = service.criar(new RequisicaoRequest(
                "Jovens", null, "Lanche", "Encontro", new BigDecimal("40.00"), null,
                "DINHEIRO", null, null, null, null, null));
        RequisicaoResponse aprovada = service.criar(new RequisicaoRequest(
                "Jovens", null, "Bolo", "Encontro", new BigDecimal("30.00"), null,
                "DINHEIRO", null, null, null, null, null));
        service.aprovar(aprovada.id(), null, null, null);

        WebApplicationException mistura = assertThrows(WebApplicationException.class,
                () -> service.juntar(aberta.id(), List.of(aprovada.id())));
        assertEquals(400, mistura.getResponse().getStatus());

        // junta duas abertas e deixa a tesouraria aprovar o total: separar deixa de ser possível
        RequisicaoResponse outra = service.criar(new RequisicaoRequest(
                "Jovens", null, "Refrigerante", "Encontro", new BigDecimal("20.00"), null,
                "DINHEIRO", null, null, null, null, null));
        RequisicaoResponse juntada = service.juntar(aberta.id(), List.of(outra.id()));
        assertTrue(juntada.podeSeparar(), "antes da avaliação dá para desfazer");

        service.aprovar(aberta.id(), new BigDecimal("60.00"), "total", null);
        assertTrue(!service.buscar(aberta.id()).podeSeparar(), "depois da avaliação, não");
        WebApplicationException separar = assertThrows(WebApplicationException.class,
                () -> service.separar(aberta.id()));
        assertEquals(400, separar.getResponse().getStatus());
    }

    /** Parte numérica de REQ-&lt;ano&gt;-&lt;seq&gt;. */
    private static int sequencia(RequisicaoResponse r) {
        return Integer.parseInt(r.numero().substring(r.numero().lastIndexOf('-') + 1));
    }
}
