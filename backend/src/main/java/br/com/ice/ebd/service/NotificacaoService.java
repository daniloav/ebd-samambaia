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
 *
 * <p>O conteúdo é ramificado pela presença:
 * <ul>
 *   <li><b>Presente</b> — e-mail de agradecimento + resumo dos itens da aula.</li>
 *   <li><b>Ausente</b> — e-mail de engajamento, convidando para o próximo encontro.</li>
 * </ul>
 * Ambos são HTML (com alternativa em texto puro para clientes sem HTML).
 */
@ApplicationScoped
public class NotificacaoService {

    private static final Logger LOG = Logger.getLogger(NotificacaoService.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SITE = "https://ebd-ices.duckdns.org";

    @Inject Mailer mailer;
    @Inject PresencaRepository presencaRepository;
    @Inject AulaRepository aulaRepository;

    @ConfigProperty(name = "ebd.notificacoes.enabled", defaultValue = "false")
    boolean habilitado;

    /**
     * Notifica os alunos da chamada da aula. Nunca lança exceção para o chamador:
     * uma falha de e-mail não pode quebrar o salvamento da chamada.
     * Observação (MVP): o envio é síncrono; migrar para assíncrono está no ROADMAP.
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
                    String assunto = p.isPresente()
                            ? "EBD — que bom ter você na aula de " + quando + "! 🙌"
                            : "EBD — sentimos sua falta 💛 te esperamos no próximo domingo";
                    String html = p.isPresente()
                            ? htmlPresente(a, aula, quando, p)
                            : htmlAusente(a, aula, quando);
                    String texto = p.isPresente()
                            ? textoPresente(a, quando, tema(aula), p)
                            : textoAusente(a, quando, tema(aula));
                    mailer.send(Mail.withHtml(a.getEmail(), assunto, html).setText(texto));
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

    // ---------- HTML ----------

    private String htmlPresente(Aluno a, Aula aula, String quando, Presenca p) {
        String corpo = ""
                + "<h1 style=\"margin:0 0 12px;font-size:20px;color:#1b3a5b;\">Olá, " + esc(a.getNome()) + "! 🙌</h1>"
                + "<p style=\"margin:0 0 18px;\">Que alegria ter você na EBD de <b>" + quando + "</b>" + temaHtml(aula)
                + ". Sua presença faz diferença na classe!</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"border-collapse:collapse;margin:0 0 18px;\">"
                + linhaItem("Presença", true)
                + linhaItem("Trouxe a Bíblia", p.isTrouxeBiblia())
                + linhaItem("Trouxe a revista", p.isTrouxeRevista())
                + linhaItem("Estudou a lição", p.isEstudouLicao())
                + "</table>"
                + "<p style=\"margin:0;\">Continue firme na caminhada — Deus abençoe! 🙏</p>";
        return shell(aula, corpo);
    }

    private String htmlAusente(Aluno a, Aula aula, String quando) {
        String corpo = ""
                + "<h1 style=\"margin:0 0 12px;font-size:20px;color:#1b3a5b;\">Olá, " + esc(a.getNome()) + "! 💛</h1>"
                + "<p style=\"margin:0 0 16px;\">Sentimos sua falta na EBD de <b>" + quando + "</b>" + temaHtml(aula) + ".</p>"
                + "<p style=\"margin:0 0 16px;\">Cada domingo é uma oportunidade de crescer na Palavra e na comunhão "
                + "com os irmãos. Já guardamos um lugar especial para você no próximo encontro — conte com a gente!</p>"
                + "<p style=\"margin:22px 0;text-align:center;\">"
                + "<a href=\"" + SITE + "\" style=\"background:#c9a24b;color:#ffffff;text-decoration:none;"
                + "padding:12px 22px;border-radius:8px;font-weight:bold;display:inline-block;\">"
                + "Te esperamos no próximo domingo</a></p>"
                + "<p style=\"margin:0;\">Com carinho, sua classe da EBD. 🙏</p>";
        return shell(aula, corpo);
    }

    /** Moldura HTML comum (cabeçalho + rodapé), compatível com clientes de e-mail (tabelas + estilo inline). */
    private String shell(Aula aula, String corpo) {
        return ""
                + "<!doctype html><html lang=\"pt-br\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>"
                + "<body style=\"margin:0;padding:0;background:#eef1f4;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background:#eef1f4;padding:24px 0;\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:600px;width:100%;background:#ffffff;border-radius:12px;overflow:hidden;"
                + "font-family:Arial,Helvetica,sans-serif;box-shadow:0 1px 4px rgba(0,0,0,.08);\">"
                + "<tr><td style=\"background:#1b3a5b;padding:22px 28px;\">"
                + "<div style=\"color:#ffffff;font-size:18px;font-weight:bold;\">Escola Bíblica Dominical</div>"
                + "<div style=\"color:#c9a24b;font-size:13px;letter-spacing:.5px;\">ICE Samambaia · Classe "
                + esc(nomeTurma(aula)) + "</div></td></tr>"
                + "<tr><td style=\"height:4px;background:#c9a24b;line-height:4px;font-size:0;\">&nbsp;</td></tr>"
                + "<tr><td style=\"padding:28px;color:#2d3748;font-size:15px;line-height:1.6;\">" + corpo + "</td></tr>"
                + "<tr><td style=\"padding:18px 28px;background:#f7f7f5;color:#8a94a6;font-size:12px;text-align:center;\">"
                + "Você recebe este e-mail porque optou por receber avisos da EBD.<br>"
                + "Igreja Cristã Evangélica em Samambaia</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private String linhaItem(String rotulo, boolean valor) {
        String celValor = valor
                ? "color:#2f855a;\">Sim ✅"
                : "color:#a0aec0;\">Não —";
        return "<tr>"
                + "<td style=\"padding:8px 0;border-bottom:1px solid #edf0f4;\">" + rotulo + "</td>"
                + "<td style=\"padding:8px 0;border-bottom:1px solid #edf0f4;text-align:right;font-weight:bold;"
                + celValor + "</td></tr>";
    }

    // ---------- Texto puro (fallback) ----------

    private String textoPresente(Aluno a, String quando, String tema, Presenca p) {
        return "Olá " + a.getNome() + ",\n\n"
                + "Que alegria ter você na EBD de " + quando + tema + "!\n\n"
                + "• Presença: Presente\n"
                + "• Trouxe a Bíblia: " + sn(p.isTrouxeBiblia()) + "\n"
                + "• Trouxe a revista: " + sn(p.isTrouxeRevista()) + "\n"
                + "• Estudou a lição: " + sn(p.isEstudouLicao()) + "\n\n"
                + "Continue firme — Deus abençoe!\nEscola Bíblica Dominical — ICE Samambaia";
    }

    private String textoAusente(Aluno a, String quando, String tema) {
        return "Olá " + a.getNome() + ",\n\n"
                + "Sentimos sua falta na EBD de " + quando + tema + ".\n"
                + "Já guardamos um lugar para você no próximo encontro — te esperamos!\n"
                + SITE + "\n\n"
                + "Com carinho, sua classe da EBD.\nEscola Bíblica Dominical — ICE Samambaia";
    }

    // ---------- Helpers ----------

    private String tema(Aula aula) {
        return aula.getTema() != null && !aula.getTema().isBlank() ? " (" + aula.getTema() + ")" : "";
    }

    private String temaHtml(Aula aula) {
        return aula.getTema() != null && !aula.getTema().isBlank() ? " — <i>" + esc(aula.getTema()) + "</i>" : "";
    }

    private String nomeTurma(Aula aula) {
        return aula.getClasse() != null && aula.getClasse().getNome() != null ? aula.getClasse().getNome() : "Adultos";
    }

    private String sn(boolean b) {
        return b ? "Sim" : "Não";
    }

    /** Escapa caracteres que quebrariam o HTML (nome/tema vêm de input do usuário). */
    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
