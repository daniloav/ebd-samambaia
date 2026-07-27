package br.com.ice.ebd;

import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Classe;
import br.com.ice.ebd.service.NotificacaoService;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class NotificacaoServiceTest {

    @Inject NotificacaoService notificacaoService;
    @Inject MockMailbox mailbox;
    @Inject Fixtures fx;

    @BeforeEach
    void limpaCaixa() {
        mailbox.clear();
    }

    @Test
    @TestTransaction
    void campanhaEnviaSomenteParaQuemTemEmail() {
        Classe c = fx.classe("Turma Camp");
        Aluno comEmail = fx.aluno("Com", c, "com@teste.local", true);
        Aluno semEmail = fx.aluno("Sem", c, null, true);

        int enviados = notificacaoService.enviarCampanha(
                "Título", "Mensagem", List.of(comEmail, semEmail), "Turma Camp", List.of());

        assertEquals(1, enviados);
        // O envio agora é assíncrono (EventBus): espera a entrega no mailbox.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals(1, mailbox.getTotalMessagesSent());
            assertEquals(1, mailbox.getMailMessagesSentTo("com@teste.local").size());
        });
    }
}
