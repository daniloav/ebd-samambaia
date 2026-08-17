package br.com.ice.ebd.service;

import br.com.ice.ebd.model.Aluno;
import br.com.ice.ebd.model.CampanhaImagem;
import br.com.ice.ebd.model.Aula;
import br.com.ice.ebd.model.Presenca;
import br.com.ice.ebd.model.Prova;
import br.com.ice.ebd.model.RequisicaoTesouraria;
import br.com.ice.ebd.model.TextoBiblicoAula;
import br.com.ice.ebd.model.Visitante;
import br.com.ice.ebd.repository.AulaRepository;
import br.com.ice.ebd.repository.PresencaRepository;
import io.quarkus.mailer.Mail;
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
 *   <li><b>Ausente com falta justificada</b> — e-mail <b>acolhedor</b>: reconhece a justificativa,
 *       não cobra e coloca a classe à disposição.</li>
 *   <li><b>Ausente sem justificativa</b> — e-mail de engajamento, convidando para o próximo encontro.</li>
 * </ul>
 * Todos são HTML (com alternativa em texto puro para clientes sem HTML).
 */
@ApplicationScoped
public class NotificacaoService {

    private static final Logger LOG = Logger.getLogger(NotificacaoService.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SITE = "https://ebd-ices.duckdns.org";

    @Inject EmailDispatcher dispatcher;
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
     * <p>Só notifica <b>eventos novos</b>: guarda por presença a assinatura do estado já enviado
     * (presente+itens) e pula quem não mudou desde o último e-mail. Retorna quantos e-mails novos
     * foram enviados. Observação (MVP): envio síncrono; assíncrono está no ROADMAP.
     */
    @Transactional
    public int notificarChamada(Long aulaId) {
        if (!habilitado) {
            return 0;
        }
        try {
            Aula aula = aulaRepository.findById(aulaId);
            if (aula == null) {
                return 0;
            }
            String quando = aula.getData().format(DATA);
            int enviados = 0;
            for (Presenca p : presencaRepository.listarPorAula(aulaId)) {
                Aluno a = p.getAluno();
                if (a.getEmail() == null || a.getEmail().isBlank() || !a.isRecebeNotificacoes()) {
                    continue;
                }
                String assinatura = assinaturaChamada(p);
                if (assinatura.equals(p.getNotificadaAssinatura())) {
                    continue; // mesmo evento, nada mudou para este aluno — não reenvia
                }
                try {
                    boolean justificada = !p.isPresente() && p.isJustificada();
                    String assunto;
                    String html;
                    String texto;
                    if (p.isPresente()) {
                        assunto = "EBD — que bom ter você na aula de " + quando + "! 🙌";
                        html = htmlPresente(a, aula, quando, p);
                        texto = textoPresente(a, quando, tema(aula), p);
                    } else if (justificada) {
                        // Falta justificada: nada de cobrança — mensagem de acolhimento.
                        assunto = "EBD — tudo bem por aí? Você faz falta e está em nossas orações 💛";
                        html = htmlAusenteJustificada(a, aula, quando, p);
                        texto = textoAusenteJustificada(a, quando, tema(aula), p);
                    } else {
                        assunto = "EBD — sentimos sua falta 💛 te esperamos no próximo domingo";
                        html = htmlAusente(a, aula, quando);
                        texto = textoAusente(a, quando, tema(aula));
                    }
                    dispatcher.enfileirar(Mail.withHtml(a.getEmail(), assunto, html).setText(texto));
                    p.setNotificadaAssinatura(assinatura); // marca o estado notificado (evita reenvio)
                    enviados++;
                } catch (Exception e) {
                    LOG.warnf("Falha ao enviar e-mail para %s: %s", a.getEmail(), e.getMessage());
                }
            }
            LOG.infof("Notificações da chamada da aula %d: %d e-mail(s) novo(s) enviado(s).", aulaId, enviados);
            return enviados;
        } catch (Exception e) {
            LOG.warnf("Notificação da chamada %d falhou: %s", aulaId, e.getMessage());
            return 0;
        }
    }

    /**
     * Assinatura do e-mail que seria enviado: ausente ("A"), ausente com falta justificada ("AJ")
     * ou presente com os itens ("P" + b/r/l). "A" e "AJ" são conteúdos diferentes, então justificar
     * a falta depois de já ter notificado dispara o e-mail acolhedor.
     */
    private static String assinaturaChamada(Presenca p) {
        if (!p.isPresente()) {
            return p.isJustificada() ? "AJ" : "A";
        }
        return "P" + (p.isTrouxeBiblia() ? '1' : '0') + (p.isTrouxeRevista() ? '1' : '0')
                + (p.isEstudouLicao() ? '1' : '0');
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
                dispatcher.enfileirar(mail);
                enviados++;
            } catch (Exception e) {
                LOG.warnf("Falha ao enviar campanha para %s: %s", a.getEmail(), e.getMessage());
            }
        }
        LOG.infof("Campanha '%s' (%s): %d e-mail(s) enviado(s) com %d imagem(ns).",
                limparParaLog(titulo), limparParaLog(turmaLabel), enviados, imagens != null ? imagens.size() : 0);
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

    /**
     * Falta <b>justificada</b>: mensagem acolhedora. Reconhece o aviso do aluno, não cobra
     * presença nem fala em "sentimos sua falta" como cobrança, e oferece ajuda da classe.
     */
    private String htmlAusenteJustificada(Aluno a, Aula aula, String quando, Presenca p) {
        String corpo = ""
                + "<h1 style=\"margin:0 0 12px;font-size:20px;color:#1b3a5b;\">Olá, " + esc(a.getNome()) + "! 💛</h1>"
                + "<p style=\"margin:0 0 16px;\">Sua ausência na EBD de <b>" + quando + "</b>" + temaHtml(aula)
                + " foi registrada como <b>falta justificada</b>. Está tudo certo — obrigado por nos avisar.</p>"
                + motivoHtml(p)
                + "<p style=\"margin:0 0 16px;\">A vida tem imprevistos, e um domingo longe da classe não muda em nada "
                + "o seu lugar entre nós. Estamos orando por você e torcendo para que tudo se resolva bem.</p>"
                + "<p style=\"margin:0 0 16px;\">Se precisar de alguma coisa — uma oração, uma conversa ou um resumo "
                + "da lição que passou — é só falar com a gente. Quando puder voltar, será uma alegria te receber.</p>"
                + "<p style=\"margin:22px 0;text-align:center;\">"
                + "<a href=\"" + SITE + "\" style=\"background:#c9a24b;color:#ffffff;text-decoration:none;"
                + "padding:12px 22px;border-radius:8px;font-weight:bold;display:inline-block;\">"
                + "Acompanhar a EBD pelo app</a></p>"
                + "<p style=\"margin:0;\">Com carinho, sua classe da EBD. 🙏</p>";
        return shell(nomeTurma(aula), corpo);
    }

    /** Repete o motivo informado na justificativa (quando houver), para o aluno ver o que ficou registrado. */
    private String motivoHtml(Presenca p) {
        String motivo = p.getJustificativaMotivo();
        if (motivo == null || motivo.isBlank()) {
            return "";
        }
        return "<p style=\"margin:0 0 16px;padding:12px 14px;background:#f7f7f5;border-left:3px solid #c9a24b;"
                + "border-radius:6px;color:#556;\"><b>Motivo registrado:</b> " + esc(motivo) + "</p>";
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
    // ==================== Tesouraria (requisições) ====================

    private static final String TES = "Tesouraria";

    private static String moeda(java.math.BigDecimal v) {
        if (v == null) return "—";
        return "R$ " + v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
    /** Em oferta de amor (PIX de terceiro) não há nota fiscal: o documento é o comprovante. */
    private static String docPrestacao(RequisicaoTesouraria r) {
        return r.isPixParaTerceiro() ? "comprovante da transferência" : "nota fiscal";
    }

    /** Bloco com o beneficiário, quando o PIX vai para a conta de um terceiro. */
    private String blocoBeneficiario(RequisicaoTesouraria r) {
        if (!r.isPixParaTerceiro()) {
            return "";
        }
        return "<p style=\"margin:0 0 6px;\"><b>Beneficiário (oferta de amor):</b> " + esc(r.getPixBeneficiarioNome())
                + " · PIX " + esc(r.getPixTipo() != null ? r.getPixTipo().name().toLowerCase() : "")
                + ": " + esc(r.getPixChave()) + "</p>"
                + (r.getPixBeneficiarioObs() != null && !r.getPixBeneficiarioObs().isBlank()
                    ? "<p style=\"margin:0 0 6px;\">" + esc(r.getPixBeneficiarioObs()) + "</p>" : "");
    }

    private String nomeSolicitante(RequisicaoTesouraria r) {
        var u = r.getSolicitante();
        return esc(u.getAluno() != null ? u.getAluno().getNome() : u.getUsername());
    }

    /** Alerta aos tesoureiros: nova requisição aguardando aprovação. */
    public void avisarNovaRequisicao(RequisicaoTesouraria r, List<String> emailsTesoureiros) {
        if (!habilitado || emailsTesoureiros == null || emailsTesoureiros.isEmpty()) return;
        String corpo = "<h1 style=\"margin:0 0 12px;font-size:20px;color:#1b3a5b;\">Nova requisição " + esc(r.getNumero()) + "</h1>"
                + "<p style=\"margin:0 0 6px;\"><b>" + nomeSolicitante(r) + "</b> (" + esc(r.getMinisterio())
                + ") solicitou <b>" + moeda(r.getValorSolicitado()) + "</b>.</p>"
                + "<p style=\"margin:0 0 6px;\"><b>Destinação:</b> " + esc(r.getDestinacao()) + "</p>"
                + "<p style=\"margin:0 0 6px;\"><b>Motivo:</b> " + esc(r.getMotivo()) + "</p>"
                + blocoBeneficiario(r)
                + "<p style=\"margin:12px 0 0;\">Acesse o app para aprovar ou negar. <a href=\"" + SITE + "\">Abrir</a></p>";
        String texto = "Nova requisição " + r.getNumero() + " de " + r.getSolicitante().getUsername()
                + " (" + r.getMinisterio() + "): " + moeda(r.getValorSolicitado()) + ". Acesse o app para avaliar.";
        for (String em : emailsTesoureiros) {
            try {
                dispatcher.enfileirar(Mail.withHtml(em, "Tesouraria — nova requisição " + r.getNumero(), shell(TES, corpo)).setText(texto));
            } catch (Exception e) {
                LOG.warnf("Falha ao avisar tesoureiro %s: %s", em, e.getMessage());
            }
        }
    }

    /** Aviso ao solicitante: requisição aprovada ou negada. */
    public void avisarRequisicaoAvaliada(RequisicaoTesouraria r) {
        var u = r.getSolicitante();
        if (!habilitado || u.getEmail() == null || u.getEmail().isBlank()) return;
        boolean aprovada = r.getStatus() == br.com.ice.ebd.model.StatusRequisicao.APROVADA;
        String assunto = aprovada
                ? "Tesouraria — requisição " + r.getNumero() + " APROVADA ✅"
                : "Tesouraria — requisição " + r.getNumero() + " negada";
        String corpo = "<h1 style=\"margin:0 0 12px;font-size:20px;color:#1b3a5b;\">Requisição " + esc(r.getNumero())
                + (aprovada ? " aprovada ✅" : " negada") + "</h1>"
                + (aprovada
                    ? "<p style=\"margin:0 0 8px;\">Sua requisição foi <b>aprovada</b> no valor de <b>"
                        + moeda(r.getValorAprovado() != null ? r.getValorAprovado() : r.getValorSolicitado()) + "</b>.</p>"
                        + "<p style=\"margin:0 0 16px;\">Após usar o recurso, <b>finalize anexando a "
                        + docPrestacao(r) + "</b> no app. Enquanto não finalizar, você receberá lembretes diários.</p>"
                    : "<p style=\"margin:0 0 16px;\">Sua requisição foi <b>negada</b>.</p>")
                + (r.getParecerTesoureiro() != null && !r.getParecerTesoureiro().isBlank()
                    ? "<p style=\"margin:0 0 16px;\"><b>Observação do tesoureiro:</b> " + esc(r.getParecerTesoureiro()) + "</p>" : "")
                + "<p style=\"margin:0;\"><a href=\"" + SITE + "\">Abrir o app</a></p>";
        String texto = "Requisição " + r.getNumero() + (aprovada ? " APROVADA (" + moeda(r.getValorAprovado()) + "). Anexe a " + docPrestacao(r) + " após usar." : " negada.")
                + (r.getParecerTesoureiro() != null ? " Obs.: " + r.getParecerTesoureiro() : "");
        try { dispatcher.enfileirar(Mail.withHtml(u.getEmail(), assunto, shell(TES, corpo)).setText(texto)); }
        catch (Exception e) { LOG.warnf("Falha ao avisar solicitante %s: %s", u.getEmail(), e.getMessage()); }
    }

    /** Aviso aos tesoureiros: prestação de contas concluída (nota anexada). */
    public void avisarRequisicaoFinalizada(RequisicaoTesouraria r, List<String> emailsTesoureiros) {
        if (!habilitado || emailsTesoureiros == null || emailsTesoureiros.isEmpty()) return;
        String corpo = "<h1 style=\"margin:0 0 12px;font-size:20px;color:#1b3a5b;\">Requisição " + esc(r.getNumero()) + " finalizada</h1>"
                + "<p style=\"margin:0 0 6px;\"><b>" + nomeSolicitante(r) + "</b> anexou a " + docPrestacao(r)
                + " e finalizou a prestação de contas.</p>"
                + blocoBeneficiario(r)
                + "<p style=\"margin:0 0 16px;\"><b>Valor gasto:</b> " + moeda(r.getValorGasto())
                + " · <b>Aprovado:</b> " + moeda(r.getValorAprovado()) + "</p>"
                + "<p style=\"margin:0;\"><a href=\"" + SITE + "\">Ver no app</a></p>";
        String texto = "Requisição " + r.getNumero() + " finalizada por " + r.getSolicitante().getUsername()
                + ". Valor gasto: " + moeda(r.getValorGasto()) + ".";
        for (String em : emailsTesoureiros) {
            try { dispatcher.enfileirar(Mail.withHtml(em, "Tesouraria — requisição " + r.getNumero() + " finalizada", shell(TES, corpo)).setText(texto)); }
            catch (Exception e) { LOG.warnf("Falha ao avisar finalização a %s: %s", em, e.getMessage()); }
        }
    }

    /** Lembrete diário ao solicitante para anexar a nota fiscal. Retorna true se enviou. */
    public boolean cobrarNotaFiscal(RequisicaoTesouraria r) {
        var u = r.getSolicitante();
        if (!habilitado || u.getEmail() == null || u.getEmail().isBlank()) return false;
        String doc = docPrestacao(r);
        String corpo = "<h1 style=\"margin:0 0 12px;font-size:20px;color:#1b3a5b;\">Pendência: " + doc + " da " + esc(r.getNumero()) + "</h1>"
                + "<p style=\"margin:0 0 8px;\">A requisição <b>" + esc(r.getNumero()) + "</b> (" + esc(r.getMinisterio())
                + ", aprovada em " + moeda(r.getValorAprovado()) + ") ainda está <b>aguardando a " + doc + "</b>.</p>"
                + "<p style=\"margin:0 0 16px;\">Por favor, finalize a prestação de contas no app.</p>"
                + "<p style=\"margin:0;\"><a href=\"" + SITE + "\">Anexar agora</a></p>";
        String texto = "Pendência: anexe a " + doc + " da requisição " + r.getNumero() + " no app.";
        try {
            dispatcher.enfileirar(Mail.withHtml(u.getEmail(), "Tesouraria — pendência de " + doc + " (" + r.getNumero() + ")", shell(TES, corpo)).setText(texto));
            return true;
        } catch (Exception e) { LOG.warnf("Falha ao cobrar nota de %s: %s", u.getEmail(), e.getMessage()); return false; }
    }

    /**
     * E-mail de recuperação de acesso (transacional): usuário + link de redefinição.
     * Enviado SEMPRE (não depende do toggle de notificações — é ação de segurança do usuário).
     */
    public void enviarRecuperacaoSenha(String email,
                                       java.util.List<RecuperacaoSenhaService.RecuperacaoItem> itens,
                                       long validadeMinutos) {
        StringBuilder c = new StringBuilder();
        c.append("<h1 style=\"margin:0 0 12px;font-size:20px;color:#1b3a5b;\">Recuperar o seu acesso</h1>");
        c.append("<p style=\"margin:0 0 14px;\">Recebemos um pedido para recuperar o seu acesso à Escola B\u00edblica. "
                + "Se n\u00e3o foi voc\u00ea, pode ignorar este e-mail com seguran\u00e7a.</p>");
        for (RecuperacaoSenhaService.RecuperacaoItem it : itens) {
            String link = SITE + "/redefinir?token=" + it.token();
            c.append("<div style=\"border:1px solid #e2e8f0;border-radius:10px;padding:14px 16px;margin:0 0 12px;\">");
            c.append("<p style=\"margin:0 0 10px;\">Seu usu\u00e1rio: <b>").append(esc(it.username())).append("</b></p>");
            c.append("<a href=\"").append(link).append("\" style=\"display:inline-block;background:#1b3a5b;color:#fff;"
                    + "text-decoration:none;padding:10px 18px;border-radius:8px;font-weight:bold;\">Redefinir a senha</a>");
            c.append("</div>");
        }
        c.append("<p style=\"margin:0;font-size:12px;color:#8a94a6;\">O link expira em ")
         .append(validadeMinutos).append(" minutos e s\u00f3 pode ser usado uma vez.</p>");

        StringBuilder txt = new StringBuilder("Recuperar acesso \u00e0 EBD ICE Samambaia.\n\n");
        for (RecuperacaoSenhaService.RecuperacaoItem it : itens) {
            txt.append("Usu\u00e1rio: ").append(it.username())
               .append("\nRedefinir: ").append(SITE).append("/redefinir?token=").append(it.token()).append("\n\n");
        }
        txt.append("O link expira em ").append(validadeMinutos).append(" minutos (uso \u00fanico).");

        dispatcher.enfileirar(Mail.withHtml(email, "Recuperar acesso \u2014 EBD ICE Samambaia",
                shell("Acesso", c.toString())).setText(txt.toString()));
    }

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

    private String textoAusenteJustificada(Aluno a, String quando, String tema, Presenca p) {
        String motivo = p.getJustificativaMotivo() != null && !p.getJustificativaMotivo().isBlank()
                ? "Motivo registrado: " + p.getJustificativaMotivo() + "\n\n" : "";
        return "Olá " + a.getNome() + ",\n\n"
                + "Sua ausência na EBD de " + quando + tema + " foi registrada como falta justificada. "
                + "Está tudo certo — obrigado por nos avisar.\n\n"
                + motivo
                + "A vida tem imprevistos, e um domingo longe da classe não muda em nada o seu lugar entre nós. "
                + "Estamos orando por você.\n"
                + "Se precisar de uma oração, de uma conversa ou de um resumo da lição, é só falar com a gente.\n"
                + SITE + "\n\n"
                + "Com carinho, sua classe da EBD.\nEscola Bíblica Dominical — ICE Samambaia";
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

    // ---------- Lembrete de chamada pendente ----------

    /**
     * Lembra o(s) professor(es) de que a chamada da aula de hoje ainda não foi feita.
     * Disparado de hora em hora a partir das 12h enquanto a chamada não for salva.
     * Retorna quantos e-mails foram enfileirados; nunca lança exceção para o chamador.
     */
    public int lembrarChamadaPendente(Aula aula, List<String> emails) {
        if (!habilitado || emails == null || emails.isEmpty()) {
            return 0;
        }
        String turma = nomeTurma(aula);
        String quando = aula.getData().format(DATA);
        String assunto = "EBD — a chamada de hoje (" + turma + ") ainda não foi feita 📋";
        String corpo = ""
                + "<h1 style=\"margin:0 0 12px;font-size:20px;color:#1b3a5b;\">Chamada pendente 📋</h1>"
                + "<p style=\"margin:0 0 16px;\">A aula de <b>" + quando + "</b> da turma <b>" + esc(turma) + "</b>"
                + temaHtml(aula) + " ainda está <b>sem chamada registrada</b>.</p>"
                + "<p style=\"margin:0 0 16px;\">Registre a presença da turma para que o ranking, os relatórios "
                + "e a frequência dos alunos fiquem em dia.</p>"
                + "<p style=\"margin:22px 0;text-align:center;\">"
                + "<a href=\"" + SITE + "/chamada\" style=\"background:#c9a24b;color:#ffffff;text-decoration:none;"
                + "padding:12px 22px;border-radius:8px;font-weight:bold;display:inline-block;\">Fazer a chamada agora</a></p>"
                + "<p style=\"margin:0;color:#8a94a6;font-size:13px;\">Você continuará recebendo este lembrete "
                + "de hora em hora até a chamada ser registrada.</p>";
        String texto = "A chamada da aula de " + quando + " (" + turma + ") ainda não foi feita.\n"
                + "Registre a presença no app: " + SITE + "/chamada\n\n"
                + "Escola Bíblica Dominical — ICE Samambaia";
        int enviados = 0;
        for (String em : emails) {
            if (em == null || em.isBlank()) {
                continue;
            }
            try {
                dispatcher.enfileirar(Mail.withHtml(em, assunto, shell(turma, corpo)).setText(texto));
                enviados++;
            } catch (Exception e) {
                LOG.warnf("Falha ao lembrar %s da chamada da aula %d: %s", em, aula.getId(), e.getMessage());
            }
        }
        return enviados;
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
            dispatcher.enfileirar(Mail.withHtml(v.getEmail(),
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
                dispatcher.enfileirar(Mail.withHtml(em, assunto, html).setText(texto));
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

    // ---------- Status automático (inativação / promoção de visitante) ----------

    /**
     * Avisa o aluno de que foi inativado por faltas seguidas. Respeita o toggle e exige e-mail;
     * ignora o opt-in (é um aviso de status da conta). Retorna {@code true} se enfileirado. Nunca lança.
     */
    public boolean avisarAlunoInativado(Aluno a, int faltasSeguidas) {
        if (!habilitado || a.getEmail() == null || a.getEmail().isBlank()) {
            return false;
        }
        try {
            String corpo = ""
                    + "<h1 style=\"margin:0 0 12px;font-size:20px;color:#1b3a5b;\">Sentimos a sua falta, "
                    + esc(a.getNome()) + " \uD83D\uDC9B</h1>"
                    + "<p style=\"margin:0 0 16px;\">Notamos que voc\u00ea faltou \u00e0s \u00faltimas <b>" + faltasSeguidas
                    + " aulas</b> seguidas da Escola B\u00edblica Dominical. Por isso, o seu cadastro foi marcado como "
                    + "<b>inativo</b> \u2014 mas voc\u00ea \u00e9 sempre bem-vindo(a) de volta!</p>"
                    + "<p style=\"margin:0 0 16px;\">Se houve um motivo para as aus\u00eancias, voc\u00ea pode <b>justificar suas faltas</b> "
                    + "na sua \u00e1rea do aluno. Para reativar o seu cadastro, fale com o seu professor ou com a lideran\u00e7a.</p>"
                    + "<p style=\"margin:22px 0;text-align:center;\">"
                    + "<a href=\"" + SITE + "\" style=\"background:#c9a24b;color:#ffffff;text-decoration:none;"
                    + "padding:12px 22px;border-radius:8px;font-weight:bold;display:inline-block;\">Acessar minha \u00e1rea</a></p>"
                    + "<p style=\"margin:0;\">Contamos com voc\u00ea,<br>Escola B\u00edblica Dominical \u2014 ICE Samambaia \uD83D\uDE4F</p>";
            String texto = "Sentimos a sua falta, " + a.getNome() + ".\n\n"
                    + "Voc\u00ea faltou \u00e0s \u00faltimas " + faltasSeguidas + " aulas seguidas e o seu cadastro foi marcado como inativo. "
                    + "Voc\u00ea pode justificar suas faltas na sua \u00e1rea do aluno; para reativar, fale com o professor ou a lideran\u00e7a.\n\n"
                    + "Escola B\u00edblica Dominical \u2014 ICE Samambaia";
            dispatcher.enfileirar(Mail.withHtml(a.getEmail(),
                    "Sentimos a sua falta na EBD \uD83D\uDC9B", shell("Frequ\u00eancia", corpo)).setText(texto));
            LOG.infof("Aviso de inativa\u00e7\u00e3o enviado a %s.", a.getEmail());
            return true;
        } catch (Exception e) {
            LOG.warnf("Falha ao avisar inativa\u00e7\u00e3o de %s: %s", a.getEmail(), e.getMessage());
            return false;
        }
    }

    /**
     * D\u00e1 boas-vindas ao visitante que virou aluno (3 aulas seguidas). Respeita o toggle e exige e-mail.
     * Informa o login; a senha do 1\u00ba acesso \u00e9 fornecida pela lideran\u00e7a (n\u00e3o vai por e-mail). Nunca lança.
     */
    public boolean avisarVisitantePromovido(Aluno novo, String login) {
        if (!habilitado || novo.getEmail() == null || novo.getEmail().isBlank()) {
            return false;
        }
        try {
            String turma = novo.getClasse() != null ? novo.getClasse().getNome() : "EBD";
            String acesso = (login != null && !login.isBlank())
                    ? "<p style=\"margin:0 0 16px;\">O seu acesso ao sistema foi criado. Login: <b>" + esc(login)
                      + "</b>. A senha do 1\u00ba acesso \u00e9 informada pela lideran\u00e7a (voc\u00ea a troca no primeiro login).</p>"
                    : "";
            String corpo = ""
                    + "<h1 style=\"margin:0 0 12px;font-size:20px;color:#1b3a5b;\">Agora voc\u00ea \u00e9 da turma, "
                    + esc(novo.getNome()) + "! \uD83C\uDF89</h1>"
                    + "<p style=\"margin:0 0 16px;\">Voc\u00ea participou de <b>3 encontros seguidos</b> e agora faz parte oficialmente "
                    + "da nossa Escola B\u00edblica Dominical \u2014 turma <b>" + esc(turma) + "</b>. Que alegria ter voc\u00ea conosco!</p>"
                    + acesso
                    + "<p style=\"margin:22px 0;text-align:center;\">"
                    + "<a href=\"" + SITE + "\" style=\"background:#c9a24b;color:#ffffff;text-decoration:none;"
                    + "padding:12px 22px;border-radius:8px;font-weight:bold;display:inline-block;\">Acessar o sistema</a></p>"
                    + "<p style=\"margin:0;\">Com carinho,<br>Escola B\u00edblica Dominical \u2014 ICE Samambaia \uD83D\uDE4F</p>";
            String texto = "Parab\u00e9ns, " + novo.getNome() + "! Voc\u00ea participou de 3 encontros seguidos e agora \u00e9 aluno(a) da EBD "
                    + "(turma " + turma + ")."
                    + (login != null && !login.isBlank() ? " Login: " + login + " (senha do 1\u00ba acesso com a lideran\u00e7a)." : "")
                    + "\n\nEscola B\u00edblica Dominical \u2014 ICE Samambaia";
            dispatcher.enfileirar(Mail.withHtml(novo.getEmail(),
                    "Bem-vindo(a) como aluno(a) da EBD! \uD83C\uDF89", shell(turma, corpo)).setText(texto));
            LOG.infof("Boas-vindas de promo\u00e7\u00e3o enviadas a %s.", novo.getEmail());
            return true;
        } catch (Exception e) {
            LOG.warnf("Falha ao avisar promo\u00e7\u00e3o de %s: %s", novo.getEmail(), e.getMessage());
            return false;
        }
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
        java.time.LocalDate hoje = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        if (hoje.equals(a.getAniversarioNotificadoEm())) {
            return false; // já parabenizado hoje — não reenvia
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
            dispatcher.enfileirar(Mail.withHtml(a.getEmail(),
                    "Feliz aniversário! 🎉 — EBD ICE Samambaia", shell("Aniversário", corpo)).setText(texto));
            a.setAniversarioNotificadoEm(hoje); // dedup: não reenvia no mesmo dia
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
            dispatcher.enfileirar(Mail.withHtml(a.getEmail(),
                    "EBD — resultado da prova: " + prova.getTitulo(), shell("Provas", corpo)).setText(texto));
            return true;
        } catch (Exception e) {
            LOG.warnf("Falha ao enviar nota para %s: %s", a.getEmail(), e.getMessage());
            return false;
        }
    }

    // ==================== Leitura bíblica diária ====================

    /**
     * Envia a leitura bíblica do dia aos alunos da turma (preparação para a lição do próximo
     * encontro). {@code textoBiblico} é o texto buscado na internet — quando nulo, o e-mail sai
     * só com a referência. Nunca lança exceção; devolve quantos e-mails foram enfileirados.
     */
    public int enviarLeituraDiaria(TextoBiblicoAula leitura, String textoBiblico, List<Aluno> alunos) {
        if (!habilitado || alunos == null || alunos.isEmpty()) {
            return 0;
        }
        Aula aula = leitura.getAula();
        String turma = nomeTurma(aula);
        String referencia = leitura.getReferencia();
        String quandoAula = aula.getData().format(DATA);
        String assunto = "EBD — leitura de hoje: " + referencia + " 📖";
        int enviados = 0;
        for (Aluno a : alunos) {
            if (a.getEmail() == null || a.getEmail().isBlank()) {
                continue;
            }
            try {
                String corpo = ""
                        + "<h1 style=\"margin:0 0 12px;font-size:20px;color:#1b3a5b;\">Leitura de hoje 📖</h1>"
                        + "<p style=\"margin:0 0 16px;\">Olá, " + esc(a.getNome()) + "! A leitura de hoje, "
                        + "preparando a lição de <b>" + quandoAula + "</b>" + temaHtml(aula) + ", é:</p>"
                        + "<p style=\"margin:0 0 14px;font-size:18px;color:#1b3a5b;font-weight:bold;\">"
                        + esc(referencia) + "</p>"
                        + textoBiblicoHtml(textoBiblico)
                        + "<p style=\"margin:22px 0;text-align:center;\">"
                        + "<a href=\"" + SITE + "\" style=\"background:#c9a24b;color:#ffffff;text-decoration:none;"
                        + "padding:12px 22px;border-radius:8px;font-weight:bold;display:inline-block;\">"
                        + "Ver a EBD no app</a></p>"
                        + "<p style=\"margin:0;\">Bom dia na Palavra e Deus abençoe! 🙏</p>";
                String texto = "Olá " + a.getNome() + ",\n\n"
                        + "Leitura de hoje (preparação para a lição de " + quandoAula + "): " + referencia + "\n\n"
                        + (textoBiblico != null ? textoBiblico + "\n\n" : "")
                        + "Escola Bíblica Dominical — ICE Samambaia";
                dispatcher.enfileirar(Mail.withHtml(a.getEmail(), assunto, shell(turma, corpo)).setText(texto));
                enviados++;
            } catch (Exception e) {
                LOG.warnf("Falha ao enviar a leitura %s para %s: %s",
                        limparParaLog(referencia), limparParaLog(a.getEmail()), e.getMessage());
            }
        }
        return enviados;
    }

    /** Bloco com o texto bíblico (um versículo por linha). Vazio quando não foi possível obtê-lo. */
    private String textoBiblicoHtml(String textoBiblico) {
        if (textoBiblico == null || textoBiblico.isBlank()) {
            return "<p style=\"margin:0 0 16px;color:#8a94a6;font-size:13px;\">"
                    + "Abra a sua Bíblia nesta passagem e faça a leitura de hoje.</p>";
        }
        String corpo = esc(textoBiblico).replace("\n", "<br>");
        return "<div style=\"margin:0 0 16px;padding:14px 16px;background:#f7f7f5;border-left:3px solid #c9a24b;"
                + "border-radius:6px;color:#2d3748;font-size:15px;line-height:1.7;\">" + corpo + "</div>"
                + "<p style=\"margin:0 0 16px;color:#8a94a6;font-size:12px;\">"
                + "Texto: João Ferreira de Almeida (domínio público).</p>";
    }

    /** Neutraliza quebras de linha/caracteres de controle de valores de usuário antes de logar
     *  (evita log-injection: forjar linhas de log com \n). */
    private static String limparParaLog(String s) {
        return s == null ? null : s.replaceAll("\\p{Cntrl}", "_");
    }
}
