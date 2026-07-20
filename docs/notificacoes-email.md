# Alertas por e-mail (chamada)

Quando a chamada de uma aula é salva, o sistema envia um e-mail para **cada aluno que
optou por receber** (`recebe_notificacoes = true`) e tem **e-mail cadastrado**. O e-mail
traz a presença e os itens da aula (Bíblia, revista, lição, visitante).

- **Backend:** `NotificacaoService` (usa o Quarkus Mailer), disparado no `ChamadaResource` ao
  salvar. Nunca quebra o salvamento da chamada: falha de e-mail só vira log.
- **Opt-in por aluno:** no cadastro do aluno há o campo *e-mail* e a opção
  *"Receber alertas de chamada por e-mail"* (LGPD — só enviamos a quem consentiu).
- **Migration:** `V4__aluno_email_notificacoes.sql` (colunas `email` e `recebe_notificacoes`).

## Liga/desliga

Controlado pela flag **`ebd.notificacoes.enabled`**:

| Ambiente | Valor | Comportamento |
|---|---|---|
| **dev** (`mvn quarkus:dev`) | `true` | Mailer em **mock**: não envia de verdade, só **loga** o e-mail. |
| **produção** | `false` (padrão) | Não envia nada até você configurar o SMTP e ligar. |

## Como ligar em produção (SMTP grátis — Brevo)

1. Crie uma conta grátis em **Brevo** (ex-Sendinblue) — plano free ~300 e-mails/dia.
2. Em **SMTP & API → SMTP**, pegue: host (`smtp-relay.brevo.com`), porta `587`,
   *login* (seu e-mail) e a **chave SMTP** (senha).
3. No `.env` de produção da VM (e no secret **`OCI_ENV_FILE`** do GitHub — ver
   [`senhas-e-secrets.md`](senhas-e-secrets.md)), defina:

   ```env
   EBD_NOTIF_ENABLED=true
   EBD_MAIL_FROM=EBD ICE Samambaia <no-reply@ebd-ices.duckdns.org>
   EBD_SMTP_HOST=smtp-relay.brevo.com
   EBD_SMTP_PORT=587
   EBD_SMTP_USER=<seu-login-brevo>
   EBD_SMTP_PASS=<sua-chave-smtp-brevo>
   ```

4. Aplique na VM: `sudo docker compose up -d` (recria o backend com as novas variáveis).
   O `docker-compose.yml` já repassa essas variáveis ao backend.

> **Entregabilidade:** para o `EBD_MAIL_FROM` cair menos em spam, o ideal é validar o
> domínio no Brevo (SPF/DKIM). Enquanto não fizer isso, dá para usar um remetente já
> verificado na sua conta Brevo.

## Limitações conhecidas (roadmap)

- **Envio síncrono** dentro da transação da chamada. Para turmas grandes, migrar para
  **assíncrono** (fila/`@Blocking`/evento) — está no [ROADMAP](ROADMAP.md).
- Só o canal **e-mail** por enquanto. Telegram/WhatsApp ficam para depois.
