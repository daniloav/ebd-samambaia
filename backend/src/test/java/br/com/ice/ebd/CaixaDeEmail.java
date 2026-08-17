package br.com.ice.ebd;

import io.quarkus.mailer.MockMailbox;

import java.time.Duration;

/**
 * O envio de e-mail é <b>assíncrono</b> (EventBus): quando um teste termina, mensagens que ele
 * enfileirou ainda podem estar em voo e cair no mailbox <i>depois</i> do {@code clear()} da classe
 * seguinte — poluindo as contagens (ex.: o parabéns de aniversário de um teste vazando na campanha
 * do teste seguinte). Este helper limpa a caixa e só devolve quando ela fica <b>estável e vazia</b>,
 * garantindo que cada teste comece do zero.
 */
final class CaixaDeEmail {

    /** Janela de silêncio: se nada chegar nesse tempo, não há mais envio em voo. */
    private static final Duration SILENCIO = Duration.ofMillis(150);
    private static final Duration LIMITE = Duration.ofSeconds(5);

    private CaixaDeEmail() {}

    /** Limpa o mailbox descartando também os e-mails atrasados de testes anteriores. */
    static void limpar(MockMailbox mailbox) {
        long limite = System.nanoTime() + LIMITE.toNanos();
        do {
            mailbox.clear();
            dormir(SILENCIO);
        } while (mailbox.getTotalMessagesSent() > 0 && System.nanoTime() < limite);
        mailbox.clear();
    }

    private static void dormir(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
