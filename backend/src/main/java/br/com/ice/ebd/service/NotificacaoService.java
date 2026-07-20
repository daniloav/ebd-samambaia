package br.com.ice.ebd.service;

import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Presenca;
import br.com.ice.ebd.repository.AulaRepository;
import br.com.ice.ebd.repository.PresencaRepository;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.format.DateTimeFormatter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Envia alertas por e-mail aos alunos quando a chamada de uma aula é salva.
 * Só envia para alunos que optaram por receber (opt-in) e têm e-mail cadastrado.
 * Controlado pelo toggle {@code ebd.notificacoes.enabled}.
 */
@ApplicationScoped
public class NotificacaoService {

    private static final Logger LOG = Logger.getLogger(NotificacaoService.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Inject Mailer mailer;
    @Inject PresencaRepository presencaRepository;
    @Inject AulaRepository aulaRepository;

    @ConfigProperty(name = "ebd.notificacoes.enabled", defaultValue = "false")
    boolean habilitado;

    /**
     * Notifica os alunos da chamada da aula. Nunca lança exceção para o chamador:
     * uma falha de e-mail não pode quebrar o salvamento da chamada.
     * Observação (MVP): o envio é feito de forma síncrona; migrar para assíncrono está no ROADMAP.
     */
    @Transactional
    public void notificarChamada(Long aulaId) {
        if (!habilitado) {
            return;
        }
        try {
            Aula aula = aulaRepository.findById(aulaId);
            if (aula == null) {
                return;
            }
            String quando = aula.getData().format(DATA);
            int enviados = 0;
            for (Presenca p : presencaRepository.listarPorAula(aulaId)) {
                Aluno a = p.getAluno();
                if (a.getEmail() == null || a.getEmail().isBlank() || !a.isRecebeNotificacoes()) {
                    continue;
                }
                try {
                    mailer.send(Mail.withText(a.getEmail(),
                            "EBD — sua chamada de " + quando,
                            montarCorpo(a, aula, quando, p)));
                    enviados++;
                } catch (Exception e) {
                    LOG.warnf("Falha ao enviar e-mail para %s: %s", a.getEmail(), e.getMessage());
                }
            }
            LOG.infof("Notificações da chamada da aula %d: %d e-mail(s) enviado(s).", aulaId, enviados);
        } catch (Exception e) {
            LOG.warnf("Notificação da chamada %d falhou: %s", aulaId, e.getMessage());
        }
    }

    private String montarCorpo(Aluno a, Aula aula, String quando, Presenca p) {
        String tema = aula.getTema() != null && !aula.getTema().isBlank() ? " (" + aula.getTema() + ")" : "";
        return "Olá " + a.getNome() + ",\n\n"
                + "Registramos sua presença na EBD — aula de " + quando + tema + ":\n\n"
                + "• Presença: " + (p.isPresente() ? "Presente ✅" : "Ausente ❌") + "\n"
                + "• Trouxe a Bíblia: " + sn(p.isTrouxeBiblia()) + "\n"
                + "• Trouxe a revista: " + sn(p.isTrouxeRevista()) + "\n"
                + "• Estudou a lição: " + sn(p.isEstudouLicao()) + "\n"
                + "• Trouxe visitante: " + sn(p.isTrouxeVisitante()) + "\n\n"
                + "Deus abençoe!\nEscola Bíblica Dominical — ICE Samambaia";
    }

    private String sn(boolean b) {
        return b ? "Sim" : "Não";
    }
}
