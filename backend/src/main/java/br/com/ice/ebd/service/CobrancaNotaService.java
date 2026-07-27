package br.com.ice.ebd.service;

import br.com.ice.ebd.model.RequisicaoTesouraria;
import br.com.ice.ebd.repository.RequisicaoRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import org.jboss.logging.Logger;

/**
 * Lembrete diário de nota fiscal: todo dia às 09:00 (BRT) cobra o solicitante de cada
 * requisição APROVADA que ainda não anexou a nota. Dedup por {@code notaCobradaEm}: no máximo
 * um e-mail por requisição por dia (protege reexecuções). Mesmo padrão do batch de aniversário.
 */
@ApplicationScoped
public class CobrancaNotaService {

    private static final Logger LOG = Logger.getLogger(CobrancaNotaService.class);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Inject RequisicaoRepository repository;
    @Inject NotificacaoService notificacao;

    @Scheduled(cron = "${ebd.cobranca-nota.cron:0 0 9 * * ?}", timeZone = "America/Sao_Paulo")
    void agendado() {
        int n = enviarPendentes();
        LOG.infof("Cobrança de nota fiscal: %d lembrete(s) enviado(s).", n);
    }

    /** Cobra as requisições aprovadas sem nota; retorna quantos e-mails foram enviados. */
    @Transactional
    public int enviarPendentes() {
        LocalDate hoje = LocalDate.now(FUSO);
        int enviados = 0;
        for (RequisicaoTesouraria r : repository.aprovadasPendentesDeNota()) {
            if (hoje.equals(r.getNotaCobradaEm())) {
                continue; // já cobrado hoje
            }
            if (notificacao.cobrarNotaFiscal(r)) {
                r.setNotaCobradaEm(hoje);
                enviados++;
            }
        }
        return enviados;
    }
}
