# Roadmap e backlog — EBD Adultos

Lista viva de próximos passos e ideias. Marque o que for concluindo e adicione novas ideias.

## ✅ Concluído

> Atualizado em 2026-07-20.

- [x] **MVP completo** (chamada, desafios/provas, relatório) — backend + frontend + infra.
- [x] **Runtime validado ponta-a-ponta** com Postgres real (migrations, seed, login JWT, chamada, provas/notas, rankings, relatório).
- [x] **Deploy na OCI + site no ar**: **https://ebd-ices.duckdns.org**. VM `E2.1.Micro` (x86, 1 GB Always Free) + swap 3 GB; stack Docker (db, backend, frontend, caddy).
- [x] **HTTPS** com domínio (DuckDNS) via **Caddy + Let's Encrypt** (auto-renova; redirect HTTP→HTTPS).
- [x] **CI/CD** (GitHub Actions): CI (build back/front + Semgrep/Trivy/gitleaks) e CodeQL nas branches; **CD real** faz deploy no merge da `main` (rsync + `docker compose up -d --build`, reescreve o `.env` a partir do secret `OCI_ENV_FILE`).
- [x] **Senhas padrão trocadas** em produção (admin/professor + credenciais do banco definidas via secret).
- [x] **Multi-turma (Classes)** — entidade `Classe`, `Aluno/Aula/Prova` por classe; chamada, rankings e relatório **filtram por turma**; seletor de turma na UI (migration V3).
- [x] **Perfis/roles + CRUD de Usuários** na UI — role **ALUNO** adicionada (ADMIN/PROFESSOR/ALUNO); tela de gestão de usuários (ADMIN).
- [x] **CRUD de Aulas** na UI — listar por turma, editar data/tema, excluir (ADMIN), atalho "fazer chamada".
- [x] **Alertas por e-mail na chamada** — opt-in por aluno (`email` + `recebe_notificacoes`, migration V4, LGPD); e-mail **HTML** com mensagem **diferente para presente (agradecimento) e ausente (engajamento p/ próximo domingo)**. SMTP **Brevo** em produção (remetente validado `danilo.av@gmail.com`); toggle `ebd.notificacoes.enabled`; mock em dev. Guia: [`notificacoes-email.md`](notificacoes-email.md).

- [x] **Módulo de Campanhas** (item 5) — envio de e-mail em massa aos alunos com opt-in, por turma ou todas; histórico com contagem de enviados (migration V5). Reaproveita o `NotificacaoService`.
- [x] **PWA** (item 4, 1º passo) — app instalável (manifest + ícone + theme-color) e service worker *network-first* (offline básico; sempre fresco online; não cacheia API).

## 🟡 Evoluções funcionais (curto prazo)

- [ ] **Dashboard com gráficos** (frequência ao longo do tempo, distribuição de presença).
- [ ] **Exportar relatório** de presenças para PDF/Excel.
- [ ] **Filtro por trimestre/período letivo** em relatórios e rankings (hoje já filtra por turma, falta o recorte de período).
- [ ] **Histórico da chamada** por aluno (linha do tempo de presença/itens).
- [ ] **Aniversariantes do mês** (já temos `data_nascimento`).
- [ ] **Observações por aula/aluno** (campo de texto livre na chamada).

## 🟢 Qualidade e robustez

- [ ] **Testes automatizados**: `@QuarkusTest` para `ChamadaService`, `RelatorioService`,
      `DesafiosService`, `NotificacaoService`; testes de fluxo de autenticação.
- [ ] **Testes de front** (ao menos smoke dos serviços/guards).
- [ ] **Envio de e-mail assíncrono** — hoje o `NotificacaoService` envia **de forma síncrona** dentro da transação da chamada; migrar para assíncrono (evento/`@Blocking`/fila) para não segurar o salvamento em turmas grandes.
- [ ] **Soft-delete de aluno** (preservar histórico usando `ativo`, sem cascata destrutiva).
- [ ] **Paginação** nas listas quando crescer o volume.

## 🔵 Infra e segurança

### Segurança de migrations (evitar dor em produção)
- [x] **Backup do banco antes de cada deploy** — `scripts/backup-db.sh` chamado pelo `cd.yml` faz `pg_dump` gzipado na VM antes do `docker compose up`; valida o dump e **aborta o deploy se falhar**; retenção dos 10 últimos em `~/ebd-samambaia/backups/`. Restauração em [`migrations.md`](migrations.md).
- [x] **CI rodando as migrations contra um Postgres real** — job `Migrations · app real (Postgres)` sobe o app contra um Postgres de serviço; falha se alguma migration quebrar ou o startup divergir do schema. Pega migration ruim **antes** de produção.
- [x] **Travar o `clean` do Flyway**: `quarkus.flyway.clean-disabled=true` no `application.properties` (nada zera o schema por engano).
- [ ] **Regra de rollback**: nunca voltar o app para uma versão anterior às migrations já aplicadas sem restaurar o backup do banco correspondente — documentar em [`migrations.md`](migrations.md).


- [ ] **Backups automáticos** do Postgres (cron + `pg_dump`).
- [ ] **Chaves JWT persistentes** via volume (hoje o Docker gera novas a cada build).
- [ ] **Rotacionar a chave SMTP da Brevo** (a atual foi compartilhada em chat durante a config).
- [ ] (Opcional) **Purga de histórico do Git** para remover a chave JWT antiga, se o repo virar público.
- [ ] **Budget/alerta de custo** na OCI (garantia extra contra cobrança).
- [ ] **Proteção de branch na `main`** (bloquear push direto, exigir PR + CI verde).
      Requer **GitHub Pro** (repo privado) ou tornar o repo **público**. Hoje o fluxo
      develop→PR→main é seguido por convenção. Aplicar via `gh api PUT .../branches/main/protection`.
- [ ] **Avaliar upgrade para "Pay As You Go"** para destravar capacidade do **A1** (ARM, 6 GB).
      Contas Free Trial têm baixa prioridade na fila do A1 ("Out of capacity"); o upgrade
      costuma liberar, e os recursos **Always Free continuam US$ 0**. Precisa cadastrar cartão
      (blindar com Budget). Alternativa ao E2.1.Micro (1 GB) que estamos usando agora.

## 🧭 Módulos planejados pós-MVP (avaliados em 2026-07-19)

> Ordem sugerida original: **1 → 3 → (canal de mensagens) → 2 → 5 → 4**.
> **Status:** itens 1, 3, 2 (e-mail), **5 (campanhas)** e **4 (PWA, 1º passo)** concluídos — ver seção ✅.

### ✅ 1. CRUD de Classes (multi-turma) — CONCLUÍDO
Entidade `Classe`, `Aluno → Classe`, tudo por turma, seletor na UI (migration V3).

### ✅ 3. Perfis/roles + CRUD de Usuários — CONCLUÍDO
Role `ALUNO` + tela de CRUD de usuários (ADMIN). Base para notificações/campanhas/app.

### ✅ 2. Alertas por mensagem (presença/falta na chamada) — CONCLUÍDO (canal e-mail)
- Feito: opt-in por aluno (migration V4), `NotificacaoService` disparado ao salvar a chamada,
  e-mail **HTML** com conteúdo distinto para **presente** e **ausente**, em produção via Brevo.
- **Ainda em aberto:** envio **assíncrono** (ver Qualidade) e **outros canais** (Telegram grátis / WhatsApp).
  Tabela de canais avaliados:
  | Canal | Custo | Facilidade | Observação |
  |---|---|---|---|
  | **E-mail** (Brevo/SMTP) | grátis (300/dia) | fácil | ✅ **implementado** |
  | **Telegram Bot** | **grátis** | fácil | 100% free; aluno inicia o bot (opt-in) — próximo candidato |
  | **Push (FCM)** | grátis | média | precisa do app/PWA (item 4) |
  | **WhatsApp Cloud API (Meta)** | quase grátis p/ *utility* | **burocrático** | número Business + templates aprovados; pode gerar custo |
  | **SMS** | **pago** | média | sem tier free sustentável no BR |

### ✅ 5. Módulo de Campanhas (envio em massa) — CONCLUÍDO
- CRUD de campanha (título, mensagem, público-alvo por classe/filtro) → dispara pelo canal do item 2
  (ou push do app). Reaproveita o `NotificacaoService`. Esforço: **médio**. Depende de 2 e 3 (ambos prontos).

### ✅ 4. App Android/iOS — PWA CONCLUÍDO (1º passo)
- As APIs REST+JWT já servem um app. Decisão tomada: **PWA primeiro** (transformar o Angular atual em
  instalável/offline): sem custo de loja, reaproveita o código. Nativo (Flutter/React Native) depois,
  se necessário — publicar exige **Apple US$99/ano** e **Google Play US$25** (única vez). Esforço nativo: **alto**.

## 💡 Ideias maiores (longo prazo)

- [ ] **PWA / uso offline** para fazer chamada sem internet e sincronizar depois (item 4).
- [ ] **Alertas de faltas seguidas** (ex.: 3 ausências consecutivas → e-mail especial ao aluno/coordenação)
      e **aniversários** — reaproveita o `NotificacaoService`.
- [ ] **Premiação automática** ao fim do trimestre com base nos rankings.
- [ ] **Domínio próprio + DKIM** (sair do DuckDNS, que não permite DKIM) para o `no-reply@` entregável.

## Limitações conhecidas (estado atual)

- Exclusão de aluno/aula/prova é **destrutiva** (cascata).
- Rankings/relatórios já recortam por **turma**, mas ainda **não por trimestre/período**.
- E-mail de chamada é **síncrono** na transação (ok para turmas pequenas; ver Qualidade).
- Textos dos e-mails (presente/ausente) são **fixos no código** — ainda não configuráveis pela UI.
- Sem testes automatizados; validação por build (`mvn package` / `ng build`) e smoke manual.
- Uma única VM (1 GB) roda tudo (db + api + front + caddy) — suficiente para o MVP, não para alta disponibilidade.
