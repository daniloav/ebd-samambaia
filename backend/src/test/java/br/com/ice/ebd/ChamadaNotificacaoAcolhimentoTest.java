package br.com.ice.ebd;

import br.com.ice.ebd.dto.SalvarChamadaRequest;
import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.service.ChamadaService;
import br.com.ice.ebd.service.NotificacaoService;
import io.vertx.ext.mail.MailMessage;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Falta justificada tem que gerar uma mensagem <b>acolhedora</b> — e nao a de cobranca/engajamento
 * enviada a quem faltou sem justificar.
 */
@QuarkusTest
class ChamadaNotificacaoAcolhimentoTest {

    @Inject ChamadaService chamadaService;
    @Inject NotificacaoService notificacaoService;
    @Inject MockMailbox mailbox;
    @Inject Fixtures fx;

    @BeforeEach
    void limpaCaixa() {
        // Descarta tambem os e-mails assincronos atrasados de testes anteriores.
        CaixaDeEmail.limpar(mailbox);
    }

    @Test
    @TestSecurity(user = "admin", roles = "ADMIN")
    @TestTransaction
    void faltaJustificadaRecebeMensagemAcolhedora() {
        Classe c = fx.classe("Turma Acolhimento");
        Aluno a = fx.aluno("Irmao Fulano", c, "acolhido@ebd.test", true);
        Aula aula = fx.aula(c, LocalDate.now());

        // 1) faltou sem justificar -> e-mail de engajamento
        chamadaService.salvarChamada(aula.getId(), new SalvarChamadaRequest(
                List.of(new SalvarChamadaRequest.Item(a.getId(), false, false, false, false))));
        assertEquals(1, notificacaoService.notificarChamada(aula.getId()));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertEquals(1, mailbox.getMailMessagesSentTo("acolhido@ebd.test").size()));
        assertTrue(mailbox.getMailMessagesSentTo("acolhido@ebd.test").get(0)
                .getSubject().contains("sentimos sua falta"));

        // 2) o professor justifica a falta -> novo e-mail, agora acolhedor (assinatura A -> AJ)
        chamadaService.salvarChamada(aula.getId(), new SalvarChamadaRequest(
                List.of(new SalvarChamadaRequest.Item(a.getId(), false, false, false, false,
                        true, "Internado no hospital"))));
        assertEquals(1, notificacaoService.notificarChamada(aula.getId()));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertEquals(2, mailbox.getMailMessagesSentTo("acolhido@ebd.test").size()));

        MailMessage acolhedor = mailbox.getMailMessagesSentTo("acolhido@ebd.test").get(1);
        assertTrue(acolhedor.getSubject().contains("orações"), "assunto acolhedor: " + acolhedor.getSubject());
        assertTrue(acolhedor.getHtml().contains("falta justificada"));
        assertTrue(acolhedor.getHtml().contains("Internado no hospital"), "repete o motivo registrado");
        assertTrue(acolhedor.getText().contains("Estamos orando por você"));

        // 3) nada mudou -> nao reenvia
        assertEquals(0, notificacaoService.notificarChamada(aula.getId()));
    }
}
