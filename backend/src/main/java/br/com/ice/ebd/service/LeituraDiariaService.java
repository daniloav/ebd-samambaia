package br.com.ice.ebd.service;

import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.TextoBiblicoAula;
import br.com.ice.ebd.repository.AlunoRepository;
import br.com.ice.ebd.repository.TextoBiblicoAulaRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Leitura bíblica do dia: todo dia às <b>8h (BRT)</b>, cada leitura cadastrada para o dia da
 * semana de hoje é enviada por e-mail aos alunos da turma (ativos, com e-mail e opt-in).
 *
 * <p>A semana da lição vai de <b>segunda até o dia da aula</b>, então hoje saem as leituras das
 * aulas de hoje até daqui a 6 dias — ver {@link br.com.ice.ebd.repository.TextoBiblicoAulaRepository#paraEnviarEm}.
 * Aula adiada não envia nada. O texto bíblico vem do {@link BibliaOnlineService} e fica em cache
 * na própria leitura; se a busca falhar, o e-mail sai só com a referência.
 *
 * <p>Dedup por {@code enviadoEm}: no máximo um e-mail por leitura por dia (protege reexecução do
 * scheduler). Mesmo padrão dos demais batches — sem recuperação de "misfire".
 */
@ApplicationScoped
public class LeituraDiariaService {

    private static final Logger LOG = Logger.getLogger(LeituraDiariaService.class);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Inject TextoBiblicoAulaRepository textoRepository;
    @Inject AlunoRepository alunoRepository;
    @Inject BibliaOnlineService biblia;
    @Inject NotificacaoService notificacao;

    /** Disparo automático diário, às 08:00 (BRT). */
    @Scheduled(cron = "${ebd.leitura-diaria.cron:0 0 8 * * ?}", timeZone = "America/Sao_Paulo")
    void agendado() {
        Resultado r = enviarDoDia();
        if (r.leituras() > 0) {
            LOG.infof("Leitura diária: %d leitura(s) do dia — %d e-mail(s) enviado(s).",
                    r.leituras(), r.enviados());
        }
    }

    /** Resultado do disparo (para o log e o endpoint manual). */
    public record Resultado(int leituras, int enviados, List<String> referencias) {}

    /** Envia as leituras cujo dia é hoje. Reutilizado pelo endpoint administrativo de teste. */
    @Transactional
    public Resultado enviarDoDia() {
        LocalDate hoje = LocalDate.now(FUSO);
        List<TextoBiblicoAula> leituras = textoRepository.paraEnviarEm(hoje);
        List<String> referencias = new ArrayList<>();
        int enviados = 0;
        for (TextoBiblicoAula t : leituras) {
            referencias.add(t.getReferencia());
            List<Aluno> destinatarios =
                    alunoRepository.listarDestinatariosEmail(t.getAula().getClasse().getId());
            if (destinatarios.isEmpty()) {
                LOG.debugf("Leitura %d (%s) sem destinatários com opt-in.", t.getId(), t.getReferencia());
                continue;
            }
            int n = notificacao.enviarLeituraDiaria(t, textoBiblico(t), destinatarios);
            if (n > 0) {
                t.setEnviadoEm(hoje);
                enviados += n;
            }
        }
        return new Resultado(leituras.size(), enviados, referencias);
    }

    /**
     * Texto bíblico da leitura: usa o que já está em cache ou busca na internet (e guarda).
     * Null quando não foi possível obter — o e-mail sai então só com a referência.
     */
    private String textoBiblico(TextoBiblicoAula t) {
        if (t.getTextoCache() != null && !t.getTextoCache().isBlank()) {
            return t.getTextoCache();
        }
        String buscado = biblia.buscar(t.getReferencia());
        if (buscado != null) {
            t.setTextoCache(buscado);
            t.setTextoCacheEm(LocalDateTime.now(FUSO));
        }
        return buscado;
    }
}
