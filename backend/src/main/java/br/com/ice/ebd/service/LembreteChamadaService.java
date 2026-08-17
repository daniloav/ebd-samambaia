package br.com.ice.ebd.service;

import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Usuario;
import br.com.ice.ebd.repository.AulaRepository;
import br.com.ice.ebd.repository.UsuarioRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.jboss.logging.Logger;

/**
 * Lembrete de chamada pendente: no <b>dia da aula</b>, se a aula é válida (não adiada) e a
 * chamada ainda não foi registrada, o professor recebe um e-mail <b>de hora em hora a partir
 * das 12h (BRT)</b> até fazer a chamada.
 *
 * <p>Destinatário: o professor da aula (quando informado); se a aula não tem professor definido,
 * todos os professores ativos vinculados à turma. Dedup por {@code aula.chamadaCobradaEm}: no
 * máximo um lembrete por aula por hora (protege reexecuções do scheduler).
 *
 * <p>Mesmo padrão dos outros batches ({@link AniversarioService}, {@link CobrancaNotaService}):
 * instância única, sem recuperação de "misfire" — se a VM estiver fora do ar numa hora, aquele
 * lembrete não é reenviado (o da hora seguinte sai normalmente).
 */
@ApplicationScoped
public class LembreteChamadaService {

    private static final Logger LOG = Logger.getLogger(LembreteChamadaService.class);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Inject AulaRepository aulaRepository;
    @Inject UsuarioRepository usuarioRepository;
    @Inject NotificacaoService notificacao;

    /** Disparo automático a cada hora cheia, das 12h às 21h (BRT). */
    @Scheduled(cron = "${ebd.lembrete-chamada.cron:0 0 12-21 * * ?}", timeZone = "America/Sao_Paulo")
    void agendado() {
        Resultado r = enviarPendentes();
        if (r.aulasPendentes() > 0) {
            LOG.infof("Chamada pendente: %d aula(s) sem chamada hoje — %d e-mail(s) enviado(s).",
                    r.aulasPendentes(), r.enviados());
        }
    }

    /** Resultado do disparo (para o log e o endpoint manual). */
    public record Resultado(int aulasPendentes, int enviados, List<String> turmas) {}

    /**
     * Cobra a chamada das aulas de hoje que ainda não têm presença registrada.
     * Reutilizado pelo endpoint administrativo de teste.
     */
    @Transactional
    public Resultado enviarPendentes() {
        LocalDateTime agora = LocalDateTime.now(FUSO);
        LocalDate hoje = agora.toLocalDate();

        List<Aula> pendentes = aulaRepository.semChamadaEm(hoje);
        List<String> turmas = new ArrayList<>();
        int enviados = 0;
        for (Aula aula : pendentes) {
            turmas.add(aula.getClasse().getNome());
            if (jaCobradaNestaHora(aula, agora)) {
                continue;
            }
            List<String> destinatarios = destinatarios(aula);
            if (destinatarios.isEmpty()) {
                LOG.debugf("Aula %d sem chamada, mas sem professor com e-mail para cobrar.", aula.getId());
                continue;
            }
            int n = notificacao.lembrarChamadaPendente(aula, destinatarios);
            if (n > 0) {
                aula.setChamadaCobradaEm(agora);
                enviados += n;
            }
        }
        return new Resultado(pendentes.size(), enviados, turmas);
    }

    /** Já houve lembrete desta aula na hora corrente? (dedup contra reexecução). */
    private boolean jaCobradaNestaHora(Aula aula, LocalDateTime agora) {
        LocalDateTime ultimo = aula.getChamadaCobradaEm();
        return ultimo != null
                && ultimo.truncatedTo(ChronoUnit.HOURS).equals(agora.truncatedTo(ChronoUnit.HOURS));
    }

    /** Professor da aula; na falta dele, os professores ativos da turma. Só quem tem e-mail. */
    private List<String> destinatarios(Aula aula) {
        Usuario professor = aula.getProfessor();
        if (professor != null && professor.isAtivo() && temEmail(professor)) {
            return List.of(professor.getEmail());
        }
        return usuarioRepository.professoresDaClasse(aula.getClasse().getId()).stream()
                .filter(LembreteChamadaService::temEmail)
                .map(Usuario::getEmail)
                .distinct()
                .toList();
    }

    private static boolean temEmail(Usuario u) {
        return u.getEmail() != null && !u.getEmail().isBlank();
    }
}
