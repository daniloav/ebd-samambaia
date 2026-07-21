package br.com.ice.ebd.service;

import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.CampanhaImagem;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Presenca;
import br.com.ice.ebd.model.Prova;
import br.com.ice.ebd.model.Visitante;
import br.com.ice.ebd.repository.AulaRepository;
import br.com.ice.ebd.repository.PresencaRepository;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Envia alertas por e-mail aos alunos quando a chamada de uma aula é salva,
 * e também os e-mails em massa das campanhas. Só envia para alunos que optaram
 * por receber (opt-in) e têm e-mail cadastrado. Controlado pelo toggle
 * {@code ebd.notificacoes.enabled}.
 *
 * <p>Na chamada o conteúdo é ramificado pela presença:
 * <ul>
 *   <li><b>Presente</b> — e-mail de agradecimento + resumo dos itens da aula.</li>
 *   <li><b>Ausente</b> — e-mail de engajamento, convidando para o próximo encontro.</li>
 * </ul>
 * Todos são HTML (com alternativa em texto puro para clientes sem HTML).
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

    /** Expõe o toggle para outros serviços (ex.: campanhas) validarem antes de enviar. */
    public boolean isNotificacaoHabilitada() {
        return habilitado;
    }

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

    /**
     * Envia uma campanha (e-mail em massa) aos destinatários informados. Retorna quantos
     * e-mails foram efetivamente enviados. Falhas individuais viram log e não interrompem o lote.
     * Roda dentro da transação do chamador (CampanhaService).
     */
    public int enviarCampanha(String titulo, String mensagem, List<Aluno> destinatarios,
                              String turmaLabel, List<CampanhaImagem> imagens) {
        if (!habilitado) {
            return 0;
        }
        int enviados = 0;
        for (Aluno a : destinatarios) {
            if (a.getEmail() == null || a.getEmail().isBlank()) {
                continue;
            }
            try {
                String html = htmlCampanha(a, titulo, mensagem, turmaLabel, imagens);
                String texto = "Olá " + a.getNome() + ",\n\n" + mensagem
                        + "\n\nEscola Bíblica Dominical — ICE Samambaia";
                Mail mail = Mail.withHtml(a.getEmail(), titulo, html).setText(texto);
                if (imagens != null) {
                    for (int i = 0; i < imagens.size(); i++) {
                        CampanhaImagem img = imagens.get(i);
                        String nome = img.getNome() != null ? img.getNome() : ("imagem" + i);
                        // contentId "imgN" casa com o cid:imgN referenciado no HTML.
                        mail.addInlineAttachment(nome, img.getConteudo(), img.getTipo(), "img" + i);
                    }
                }
                mailer.send(mail);
                enviados++;
            } catch (Exception e) {
                LOG.warnf("Falha ao enviar campanha para %s: %s", a.getEmail(), e.getMessage());
            }
        }
        LOG.infof("Campanha '%s' (%s): %d e-mail(s) enviado(s) com %d imagem(ns).",
                titulo, turmaLabel, enviados, imagens != null ? imagens.size() : 0);
        return enviados;
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
        return shell(nomeTurma(aula), corpo);
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
        return shell(nomeTurma(aula), corpo);
    }

    private String htmlCampanha(Aluno a, String titulo, String mensagem, String turmaLabel,
                               List<CampanhaImagem> imagens) {
        StringBuilder arte = new StringBuilder();
        if (imagens != null) {
            for (int i = 0; i < imagens.size(); i++) {
                arte.append("<img src=\"cid:img").append(i).append("\" alt=\"\" "
                        + "style=\"display:block;width:100%;max-width:100%;height:auto;"
                        + "border-radius:8px;margin:0 0 14px;\">");
            }
        }
        String texto = esc(mensagem).replace("\n", "<br>");
        String corpo = arte
                + "<h1 style=\"margin:0 0 14px;font-size:20px;color:#1b3a5b;\">" + esc(titulo) + "</h1>"
                + "<p style=\"margin:0 0 14px;\">Olá, " + esc(a.getNome()) + "!</p>"
                + "<p style=\"margin:0 0 18px;\">" + texto + "</p>"
                + "<p style=\"margin:0;color:#556;\">Escola Bíblica Dominical — ICE Samambaia 🙏</p>";
        return shell(turmaLabel, corpo);
    }

    /** Moldura HTML comum (cabeçalho + rodapé), compatível com clientes de e-mail (tabelas + estilo inline). */
    private String shell(String turmaLabel, String corpo) {
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
                + "<div style=\"color:#c9a24b;font-size:13px;letter-spacing:.5px;\">ICE Samambaia · "
                + esc(turmaLabel) + "</div></td></tr>"
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

    /** Escapa caracteres que quebrariam o HTML (nome/tema/mensagem vêm de input do usuário). */
    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Boas-vindas ao visitante (se tiver e-mail). Nunca lança exceção. */
    public void enviarBoasVindasVisitante(Visitante v) {
        if (!habilitado || v.getEmail() == null || v.getEmail().isBlank()) {
            return;
        }
        try {
            String turma = v.getAula() != null && v.getAula().getClasse() != null
                    ? v.getAula().getClasse().getNome() : "EBD";
            String texto = "Olá " + v.getNome() + ",\n\n"
                    + "Que alegria receber você na nossa Escola Bíblica Dominical! "
                    + "Esperamos você no próximo domingo.\n\nEscola Bíblica Dominical — ICE Samambaia";
            mailer.send(Mail.withHtml(v.getEmail(),
                    "Bem-vindo(a) à EBD — ICE Samambaia! 🙌",
                    htmlBoasVindasVisitante(v, turma)).setText(texto));
            LOG.infof("Boas-vindas enviadas ao visitante %s.", v.getEmail());
        } catch (Exception e) {
            LOG.warnf("Falha nas boas-vindas ao visitante %s: %s", v.getEmail(), e.getMessage());
        }
    }

    /** Avisa os professores (por e-mail) sobre um novo visitante. Retorna quantos receberam. */
    public int avisarProfessoresNovoVisitante(Visitante v, java.util.List<String> emailsProfessores) {
        if (!habilitado) {
            return 0;
        }
        String turma = v.getAula() != null && v.getAula().getClasse() != null
                ? v.getAula().getClasse().getNome() : "EBD";
        String trazido = v.getTrazidoPor() != null ? v.getTrazidoPor().getNome() : "não informado";
        String assunto = "EBD — novo visitante: " + v.getNome();
        String html = htmlAvisoVisitante(v, turma, trazido);
        String texto = "Novo visitante na EBD:\n\nNome: " + v.getNome()
                + "\nTurma: " + turma + "\nTrazido por: " + trazido
                + (v.getEmail() != null && !v.getEmail().isBlank() ? "\nE-mail: " + v.getEmail() : "")
                + (v.getTelefone() != null && !v.getTelefone().isBlank() ? "\nTelefone: " + v.getTelefone() : "")
                + "\n\nVamos acolher bem!\nEscola Bíblica Dominical — ICE Samambaia";
        int enviados = 0;
        for (String em : emailsProfessores) {
            if (em == null || em.isBlank()) {
                continue;
            }
            try {
                mailer.send(Mail.withHtml(em, assunto, html).setText(texto));
                enviados++;
            } catch (Exception e) {
                LOG.warnf("Falha ao avisar professor %s sobre visitante: %s", em, e.getMessage());
            }
        }
        LOG.infof("Aviso do visitante '%s' enviado a %d professor(es).", v.getNome(), enviados);
        return enviados;
    }

    private String htmlBoasVindasVisitante(Visitante v, String turma) {
        String corpo = ""
                + "<h1 style=\"margin:0 0 12px;font-size:20px;color:#1b3a5b;\">Seja bem-vindo(a), " + esc(v.getNome()) + "! 🙌</h1>"
                + "<p style=\"margin:0 0 16px;\">Foi uma alegria ter você conosco na Escola Bíblica Dominical. "
                + "Nossa classe está de portas abertas para você!</p>"
                + "<p style=\"margin:0 0 16px;\">Esperamos ver você no próximo domingo para mais um tempo de comunhão e Palavra.</p>"
                + "<p style=\"margin:22px 0;text-align:center;\">"
                + "<a href=\"" + SITE + "\" style=\"background:#c9a24b;color:#ffffff;text-decoration:none;"
                + "padding:12px 22px;border-radius:8px;font-weight:bold;display:inline-block;\">Nos vemos no próximo domingo</a></p>"
                + "<p style=\"margin:0;\">Com carinho,<br>Escola Bíblica Dominical — ICE Samambaia 🙏</p>";
        return shell(turma, corpo);
    }

    private String htmlAvisoVisitante(Visitante v, String turma, String trazido) {
        String contato = "";
        if (v.getEmail() != null && !v.getEmail().isBlank()) {
            contato += linhaContato("E-mail", esc(v.getEmail()));
        }
        if (v.getTelefone() != null && !v.getTelefone().isBlank()) {
            contato += linhaContato("Telefone", esc(v.getTelefone()));
        }
        String corpo = ""
                + "<h1 style=\"margin:0 0 12px;font-size:20px;color:#1b3a5b;\">Novo visitante na EBD 🎉</h1>"
                + "<p style=\"margin:0 0 16px;\">Registramos um novo visitante. Vamos acolher bem!</p>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\">"
                + linhaContato("Nome", esc(v.getNome()))
                + linhaContato("Turma", esc(turma))
                + linhaContato("Trazido por", esc(trazido))
                + contato
                + "</table>";
        return shell(turma, corpo);
    }

    private String linhaContato(String rotulo, String valor) {
        return "<tr>"
                + "<td style=\"padding:8px 0;border-bottom:1px solid #edf0f4;color:#718096;\">" + rotulo + "</td>"
                + "<td style=\"padding:8px 0;border-bottom:1px solid #edf0f4;text-align:right;font-weight:bold;\">" + valor + "</td></tr>";
    }

    // ---------- Aniversário ----------

    /**
     * Felicitação de aniversário. Vai a todos os alunos ativos com e-mail (ignora o opt-in,
     * conforme decisão). Retorna {@code true} se o e-mail foi enviado. Nunca lança.
     */
    public boolean enviarFelizAniversario(Aluno a) {
        if (!habilitado || a.getEmail() == null || a.getEmail().isBlank()) {
            return false;
        }
        try {
            String corpo = ""
                    + "<h1 style=\"margin:0 0 12px;font-size:22px;color:#1b3a5b;\">Feliz aniversário, "
                    + esc(a.getNome()) + "! 🎉🎂</h1>"
                    + "<p style=\"margin:0 0 16px;\">Hoje é um dia especial e queremos celebrar com você! "
                    + "Que Deus renove as suas forças, encha o seu novo ano de vida de saúde, paz e muitas bênçãos.</p>"
                    + "<p style=\"margin:0 0 16px;\">\"O Senhor te abençoe e te guarde.\" (Números 6.24)</p>"
                    + "<p style=\"margin:0;\">Com carinho,<br>Sua família da Escola Bíblica Dominical — ICE Samambaia 🙏</p>";
            String texto = "Feliz aniversário, " + a.getNome() + "!\n\n"
                    + "Hoje é um dia especial e queremos celebrar com você. Que Deus renove as suas forças "
                    + "e encha o seu novo ano de vida de bênçãos.\n"
                    + "\"O Senhor te abençoe e te guarde.\" (Números 6.24)\n\n"
                    + "Com carinho, sua família da EBD — ICE Samambaia";
            mailer.send(Mail.withHtml(a.getEmail(),
                    "Feliz aniversário! 🎉 — EBD ICE Samambaia", shell("Aniversário", corpo)).setText(texto));
            LOG.infof("Parabéns de aniversário enviado a %s.", a.getEmail());
            return true;
        } catch (Exception e) {
            LOG.warnf("Falha ao enviar aniversário para %s: %s", a.getEmail(), e.getMessage());
            return false;
        }
    }

    // ---------- Desempenho em prova ----------

    /**
     * Envia ao aluno o desempenho numa prova corrigida. Respeita o toggle e exige e-mail;
     * o filtro de opt-in fica no chamador (ProvaService). Retorna {@code true} se enviou.
     */
    public boolean enviarNotaProva(Aluno a, Prova prova, BigDecimal nota) {
        if (!habilitado || a.getEmail() == null || a.getEmail().isBlank()) {
            return false;
        }
        try {
            BigDecimal max = prova.getNotaMaxima();
            double pct = max != null && max.signum() > 0
                    ? nota.multiply(new BigDecimal("100")).divide(max, 1, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;
            String elogio = pct >= 90 ? "Excelente desempenho! 👏"
                    : pct >= 70 ? "Muito bem, continue firme! 💪"
                    : pct >= 50 ? "Bom trabalho — dá para ir ainda mais longe!"
                    : "Não desanime: cada estudo é um passo. Conte com a gente! 💛";
            String notaFmt = nota.stripTrailingZeros().toPlainString();
            String maxFmt = max != null ? max.stripTrailingZeros().toPlainString() : "—";
            String corpo = ""
                    + "<h1 style=\"margin:0 0 12px;font-size:20px;color:#1b3a5b;\">Olá, " + esc(a.getNome()) + "!</h1>"
                    + "<p style=\"margin:0 0 16px;\">Sua prova <b>" + esc(prova.getTitulo()) + "</b> ("
                    + prova.getData().format(DATA) + ") foi corrigida. Veja o seu resultado:</p>"
                    + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                    + "style=\"border-collapse:collapse;margin:0 0 18px;\">"
                    + linhaContato("Nota", notaFmt + " / " + maxFmt)
                    + linhaContato("Aproveitamento", pct + "%")
                    + "</table>"
                    + "<p style=\"margin:0;\">" + elogio + "</p>";
            String texto = "Olá " + a.getNome() + ",\n\n"
                    + "Sua prova \"" + prova.getTitulo() + "\" foi corrigida.\n"
                    + "Nota: " + notaFmt + " / " + maxFmt + " (" + pct + "%)\n\n"
                    + elogio + "\nEscola Bíblica Dominical — ICE Samambaia";
            mailer.send(Mail.withHtml(a.getEmail(),
                    "EBD — resultado da prova: " + prova.getTitulo(), shell("Provas", corpo)).setText(texto));
            return true;
        } catch (Exception e) {
            LOG.warnf("Falha ao enviar nota para %s: %s", a.getEmail(), e.getMessage());
            return false;
        }
    }
}
