package br.com.ice.ebd.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.common.annotation.Blocking;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Envio de e-mail desacoplado do fluxo da requisição. O serviço apenas
 * {@link #enfileirar(Mail) publica} o {@link Mail} no EventBus (retorno
 * imediato) e o consumidor abaixo faz o {@code mailer.send()} em segundo plano,
 * numa worker thread ({@link Blocking @Blocking}) — sem bloquear a resposta HTTP
 * nem prender a transação do banco enquanto o SMTP responde.
 *
 * <p>É entrega <b>local</b> (mesmo JVM): o {@code Mail} trafega por referência,
 * então anexos (bytes das imagens de campanha) vão junto sem serialização.
 */
@ApplicationScoped
public class EmailDispatcher {

    /** Endereço lógico do EventBus para os e-mails a enviar. */
    static final String ENDERECO = "email-out";

    private static final Logger LOG = Logger.getLogger(EmailDispatcher.class);

    @Inject
    EventBus bus;

    @Inject
    Mailer mailer;

    /** Enfileira um e-mail para envio assíncrono (não bloqueia o chamador). */
    public void enfileirar(Mail mail) {
        bus.publish(ENDERECO, mail);
    }

    /** Consome os e-mails enfileirados e envia de fato, em segundo plano. */
    @ConsumeEvent(ENDERECO)
    @Blocking
    void enviar(Mail mail) {
        try {
            mailer.send(mail);
        } catch (Exception e) {
            LOG.warnf("Falha ao enviar e-mail (async) para %s: %s", mail.getTo(), e.getMessage());
        }
    }
}
