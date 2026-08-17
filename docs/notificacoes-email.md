# Alertas por e-mail (chamada)

Quando a chamada de uma aula é salva, o sistema envia um e-mail para **cada aluno que
optou por receber** (`recebe_notificacoes = true`) e tem **e-mail cadastrado**. O e-mail
traz a presença e os itens da aula (Bíblia, revista, lição, visitante).

- **Backend:** `NotificacaoService` (usa o Quarkus Mailer), disparado no `ChamadaResource` ao
  salvar. Nunca quebra o salvamento da chamada: falha de e-mail só vira log.

**Outros e-mails do `NotificacaoService`** (todos respeitam o toggle e nunca lançam):
- **Visitante** — boas-vindas ao visitante + aviso aos professores (ao cadastrar).
- **Campanha** — envio em massa aos alunos com opt-in.
- **Aniversário** — rotina `AniversarioService` (`@Scheduled`, 12:00 BRT) → parabéns a **todos os
  alunos ativos com e-mail** (ignora o opt-in). Teste: `POST /api/admin/aniversarios/executar`.
- **Desempenho na prova** — botão "Lançar e notificar" na tela de notas → nota/aproveitamento ao
  aluno (respeita opt-in). `POST /api/provas/{id}/notas/notificar`.
- **Chamada pendente** — rotina `LembreteChamadaService` (`@Scheduled`, de hora em hora das **12h às
  21h** BRT): no dia da aula, se a aula é válida (**não adiada**) e ainda **não tem chamada
  registrada**, cobra o **professor da aula** (ou, se ela não tem professor definido, os professores
  ativos da turma). Repete a cada hora até a chamada ser salva; dedup por `aula.chamada_cobrada_em`
  (1 e-mail por aula por hora). Turma sem aluno ativo não gera cobrança. Teste:
  `POST /api/admin/lembretes-chamada/executar`. Cron configurável em `ebd.lembrete-chamada.cron`.
- **Opt-in por aluno:** no cadastro do aluno há o campo *e-mail* e a opção
  *"Receber alertas de chamada por e-mail"* (LGPD — só enviamos a quem consentiu).
- **Migration:** `V4__aluno_email_notificacoes.sql` (colunas `email` e `recebe_notificacoes`).

## Liga/desliga

Controlado pela flag **`ebd.notificacoes.enabled`**:

| Ambiente | Valor | Comportamento |
|---|---|---|
| **dev** (`mvn quarkus:dev`) | `true` | Mailer em **mock**: não envia de verdade, só **loga** o e-mail. |
| **produção** | `false` (padrão) | Não envia nada até você configurar o SMTP e ligar. |

## Como ligar em produção (SMTP grátis — Gmail)

> **Histórico (2026-07):** começamos com o **Brevo**, mas ele passou a **rejeitar** envios
> com remetente de domínio gratuito (`@gmail.com`): pelas regras DMARC do Gmail/Yahoo
> (2024), o Brevo reescreve o remetente para `...@brevosend.com` e hoje **recusa** esse
> fallback. Só aceitaria com **domínio próprio autenticado** (DKIM) — e o DuckDNS não
> suporta os registros DNS necessários. Solução: enviar pelo **SMTP do próprio Gmail**
> com **senha de app** (o e-mail sai assinado pelo Google; DMARC ok, sem reescrita).
> Limite ~500 destinatários/dia — muito acima do uso da EBD.

1. Na conta Google remetente, ative a **verificação em 2 etapas** e crie uma
   **senha de app** em <https://myaccount.google.com/apppasswords> (16 letras, sem espaços).
2. No `.env` de produção da VM (e no secret **`OCI_ENV_FILE`** do GitHub — ver
   [`senhas-e-secrets.md`](senhas-e-secrets.md)), defina:

   ```env
   EBD_NOTIF_ENABLED=true
   EBD_MAIL_FROM=EBD ICE Samambaia <conta-remetente@gmail.com>
   EBD_SMTP_HOST=smtp.gmail.com
   EBD_SMTP_PORT=587
   EBD_SMTP_USER=conta-remetente@gmail.com
   EBD_SMTP_PASS=<senha de app>
   ```

   > O `EBD_MAIL_FROM` deve ser **a própria conta Gmail** que autentica no SMTP
   > (o Google reescreve remetentes diferentes da conta autenticada).

3. Aplique na VM: `sudo docker compose up -d` (recria o backend com as novas variáveis).
   O `docker-compose.yml` já repassa essas variáveis ao backend. (O próximo deploy do CD
   também aplica, pois envia o `.env` e recria os containers.)
4. Teste: salve uma chamada (aluno com e-mail + opt-in) ou cadastre um visitante com
   e-mail; `sudo docker logs --tail 20 ebd-backend | grep -i notifica` mostra os envios.

> **Recomendado (ver ROADMAP):** usar uma conta Gmail **dedicada** ao sistema
> (ex.: `ebd.ices@gmail.com`) em vez do e-mail pessoal — a senha de app fica restrita
> a uma conta sem dados pessoais, e o remetente fica com cara institucional.

## Limitações conhecidas (roadmap)

- **Envio síncrono** dentro da transação da chamada. Para turmas grandes, migrar para
  **assíncrono** (fila/`@Blocking`/evento) — está no [ROADMAP](ROADMAP.md).
- Só o canal **e-mail** por enquanto. Telegram/WhatsApp ficam para depois.
