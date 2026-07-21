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

## 🔍 Avaliação do sistema (2026-07-20): segurança, usabilidade, design e mobile

> Auditoria feita sobre o código/config reais. Prioridade: **P1** (fazer logo) → **P3** (quando der).

### 🔐 Segurança
- [ ] **P1 · Fechar o CORS em produção** — hoje `quarkus.http.cors.origins=/.*/` (qualquer origem) vale
      também em prod. Como o nginx faz proxy same-origin de `/api`, o CORS pode ser **desligado em prod**
      (`%prod.quarkus.http.cors=false`) e mantido aberto só em dev. 1 linha, risco zero.
- [ ] **P1 · Proteção de força-bruta no login** — `/api/auth/login` não tem limite de tentativas nem atraso.
      Mínimo: atraso incremental por usuário/IP após N falhas (in-memory já ajuda). Senhas fracas em uso
      tornam isso mais importante.
- [ ] **P1 · Headers de segurança no Caddy** — faltam `Strict-Transport-Security`, `X-Content-Type-Options`,
      `X-Frame-Options`/`frame-ancestors`, `Referrer-Policy`. Bloco `header {}` no Caddyfile, ~6 linhas.
- [ ] **P2 · Política de senha mínima** — `UsuarioRequest.senha` não valida tamanho/força
      (aceita "1"). `@Size(min=8)` + dica na UI.
- [ ] **P2 · Trocar a própria senha** — hoje só o ADMIN troca senhas (tela Usuários); o usuário não pode
      trocar a dele (e as senhas atuais foram definidas pelo admin). Endpoint `PUT /api/me/senha` + tela.
- [ ] **P2 · Chaves JWT persistentes** (já listado em Infra) — cada deploy gera chaves novas e **desloga
      todo mundo**; com volume, sessões sobrevivem ao deploy.
- [ ] **P3 · Auditoria de ações** — log de quem excluiu/alterou aluno, aula, prova, usuário (tabela
      `auditoria` simples). Útil quando houver mais professores.
- [ ] **P3 · Token em localStorage** — acessível a XSS (Angular escapa por padrão; risco baixo). Alternativa
      robusta (cookie httpOnly + CSRF) só se o app crescer.

### 🧭 Usabilidade
- [ ] **P1 · Aviso de sessão expirada** — o 401 redireciona ao login em silêncio; mostrar toast
      "Sua sessão expirou, entre novamente" (o interceptor já centraliza isso).
- [ ] **P2 · Substituir `confirm()` nativo** — 7 telas usam o diálogo do navegador (feio e inconsistente
      com o design). Criar um modal de confirmação reutilizável no padrão visual do app.
- [ ] **P2 · Busca/filtro na lista de alunos** — com turmas grandes, achar um aluno exige rolar; um campo
      de busca por nome resolve (junto da paginação já listada).
- [ ] **P3 · "Esqueci minha senha"** — fluxo de reset por e-mail (o SMTP já existe). Enquanto não houver,
      o caminho é pedir ao admin.
- [ ] **P3 · Estados vazios orientados** — telas sem dados poderiam sugerir a próxima ação
      (ex.: "Nenhuma aula — crie a primeira" com botão), em vez de só texto.

### 🎨 Design
- [ ] **P2 · Modal de confirmação própria** (mesmo item do confirm() acima — é a maior inconsistência visual).
- [ ] **P3 · Refinos visuais** — favicon PNG real (hoje emoji inline), transições/hover consistentes,
      skeleton loading nas tabelas em vez de "Carregando...".
- [ ] **P3 · Modo escuro** — o SCSS usa variáveis CSS (`--azul`, etc.), então um tema escuro é viável
      com `prefers-color-scheme` + overrides. Nice-to-have.

### 📱 Compatibilidade mobile
> O caso de uso nº 1 no celular é o professor **fazendo a chamada em sala**. Priorizar essa tela.
- [ ] **P1 · Chamada otimizada para toque** — checkboxes nativos são pequenos para dedo; aumentar a área
      de toque (célula inteira clicável, alvos ≥44px) e testar a tabela de 5 colunas em 360px de largura.
- [ ] **P2 · Responsividade além do shell** — só o layout tem breakpoint (820px); as páginas dependem de
      `tabela-scroll` (funciona, mas apertado). Nas principais (chamada, alunos), considerar linhas em
      formato cartão no mobile.
- [ ] **P2 · Ícones PNG do PWA (192/512px)** — o ícone SVG não rende bem no iOS (limitação conhecida,
      documentada no guia); PNGs fecham a instalação com visual correto em iPhone.
- [ ] **P3 · Ajustes finos de PWA** — `safe-area-inset` para notch, splash screens iOS, atalhos de app
      (`shortcuts` no manifest, ex.: "Fazer chamada" direto).

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

### Lentidão de deploy na VM de 1 GB (prioridade — deploys levam ~20 min)
- [ ] **Podar o build cache na VM** — está em ~5 GB e piora a lentidão: `docker builder prune -af`
      (ganho imediato de espaço/velocidade). Considerar rodar periodicamente (cron) ou no fim do `cd.yml`.
- [ ] **Subir para o A1 (6 GB)** via Pay-As-You-Go — **resolve de vez**: o build do Angular (`npm ci`/`ng build`)
      faz swap thrashing na VM de 1 GB (load 4–5, ~20 min por deploy). Ver item abaixo. Já mitigado com
      keepalive no SSH (o deploy sobrevive), mas continua lento até o upgrade.
- Contexto: o CD faz `docker compose up -d --build` na VM (build do frontend + backend). Na de 1 GB isso
  thrasha o swap. O keepalive no SSH evita o "Broken pipe"; o cache do `npm ci` só é reaproveitado se o
  `package.json` não mudar (por isso o bump de versão grava só o `version.ts`).


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
  | **SMS (Brevo)** | **pago** (sem free) | fácil (mesma conta) | Brevo TEM API de SMS transacional (`POST /v3/transactionalSMS/sms`) — dá pra reusar a conta/chave. Porém consome **créditos SMS pagos** (BR ~€0,03–0,05/msg, sem tier grátis) e exige remetente aprovado. Viável tecnicamente; decisão é de **custo**. |

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
