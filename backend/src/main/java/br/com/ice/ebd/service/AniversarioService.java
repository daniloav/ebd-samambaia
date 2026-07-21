package br.com.ice.ebd.service;

import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.repository.AlunoRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Primeira rotina batch do app: todo dia às 12:00 (horário de Brasília) envia uma
 * mensagem de feliz aniversário aos alunos que fazem aniversário no dia.
 *
 * <p>Instância única + cron 1x/dia; não há recuperação de "misfire" (se a VM estiver
 * fora do ar às 12h, os parabéns do dia não são reenviados). Aceitável no MVP.
 */
@ApplicationScoped
public class AniversarioService {

    private static final Logger LOG = Logger.getLogger(AniversarioService.class);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Inject AlunoRepository alunoRepository;
    @Inject NotificacaoService notificacaoService;

    /** Disparo automático diário às 12:00 BRT. */
    @Scheduled(cron = "${ebd.aniversario.cron:0 0 12 * * ?}", timeZone = "America/Sao_Paulo")
    void agendado() {
        Resultado r = enviarDoDia();
        LOG.infof("Aniversariantes de hoje: %d — e-mails enviados: %d.", r.total(), r.enviados());
    }

    /** Resultado do disparo (para o log e o endpoint de teste). */
    public record Resultado(int total, int enviados, List<String> nomes) {}

    /** Envia os parabéns dos aniversariantes de hoje (BRT). Reutilizado pelo endpoint de teste. */
    @Transactional
    public Resultado enviarDoDia() {
        LocalDate hoje = LocalDate.now(FUSO);
        List<Aluno> aniversariantes =
                alunoRepository.listarAniversariantesComEmail(hoje.getMonthValue(), hoje.getDayOfMonth());
        int enviados = 0;
        for (Aluno a : aniversariantes) {
            if (notificacaoService.enviarFelizAniversario(a)) {
                enviados++;
            }
        }
        return new Resultado(aniversariantes.size(), enviados,
                aniversariantes.stream().map(Aluno::getNome).toList());
    }
}
