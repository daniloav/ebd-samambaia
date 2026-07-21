# Roadmap e backlog — EBD Adultos

Lista viva de próximos passos e ideias. Marque o que for concluindo e adicione novas ideias.

## ✅ Concluído

> Atualizado em 2026-07-20.

- [x] **MVP completo** (chamada, desafios/provas, relatório) — backend + frontend + infra.
- [x] **Runtime validado ponta-a-ponta** com Postgres real (migrations, seed, login JWT, chamada, provas/notas, rankings, relatório).
- [x] **Deploy na OCI + site no ar**: **https://ebd-ices.duckdns.org**. **2 VMs** E2.1.Micro (app: caddy+frontend+backend · banco: Postgres) — ver [`topologia.md`](topologia.md).
- [x] **HTTPS** com domínio (DuckDNS) via **Caddy + Let's Encrypt** (auto-renova; redirect HTTP→HTTPS).
- [x] **CI/CD** (GitHub Actions): CI (build back/front + Semgrep/Trivy/gitleaks) e CodeQL; **CD** builda as imagens no runner → **GHCR privado** → a VM faz `pull` (sem build, ~2 min). Chaves JWT via volume; `.env` do secret `OCI_ENV_FILE`.
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
- [x] **P1 · Fechar o CORS em produção** — hoje `quarkus.http.cors.origins=/.*/` (qualquer origem) vale
      também em prod. Como o nginx faz proxy same-origin de `/api`, o CORS pode ser **desligado em prod**
      (`%prod.quarkus.http.cors=false`) e mantido aberto só em dev. 1 linha, risco zero.
- [x] **P1 · Proteção de força-bruta no login** — `/api/auth/login` não tem limite de tentativas nem atraso.
      Mínimo: atraso incremental por usuário/IP após N falhas (in-memory já ajuda). Senhas fracas em uso
      tornam isso mais importante.
- [x] **P1 · Headers de segurança no Caddy** — faltam `Strict-Transport-Security`, `X-Content-Type-Options`,
      `X-Frame-Options`/`frame-ancestors`, `Referrer-Policy`. Bloco `header {}` no Caddyfile, ~6 linhas.
- [x] **P2 · Política de senha mínima** — senha exige **≥ 8 caracteres** (validado no `UsuarioService` para criar/editar/trocar). `UsuarioRequest.senha`
      (aceita "1"). `@Size(min=8)` + dica na UI.
- [x] **P2 · Trocar a própria senha** — `PUT /api/me/senha` (confere senha atual, exige ≥8, recusa repetir) + tela **Minha conta** (link no rodapé). Antes só o ADMIN trocava; o usuário não podia
      trocar a dele (e as senhas atuais foram definidas pelo admin). Endpoint `PUT /api/me/senha` + tela.
- [x] **P2 · Chaves JWT persistentes** (ver Infra) — CD grava as chaves dos secrets `EBD_JWT_PRIVATE_KEY`/`EBD_JWT_PUBLIC_KEY` a cada deploy (opt-in; sem secret = comportamento antigo). Antes cada deploy gerava chaves novas e **deslogava
      todo mundo**; com volume, sessões sobrevivem ao deploy.
- [ ] **P3 · Auditoria de ações** — log de quem excluiu/alterou aluno, aula, prova, usuário (tabela
      `auditoria` simples). Útil quando houver mais professores.
- [ ] **P3 · Token em localStorage** — acessível a XSS (Angular escapa por padrão; risco baixo). Alternativa
      robusta (cookie httpOnly + CSRF) só se o app crescer.

### 🧭 Usabilidade
- [x] **P1 · Aviso de sessão expirada** — o 401 redireciona ao login em silêncio; mostrar toast
      "Sua sessão expirou, entre novamente" (o interceptor já centraliza isso).
- [x] **P2 · Substituir `confirm()` nativo** — `ConfirmService` + `ConfirmDialogComponent` (modal no padrão do app) substituem os 8 `confirm()` nativos.
- [x] **P2 · Busca/filtro na lista de alunos** — campo de busca por nome (acento-insensível) na tela de alunos.
- [ ] **P3 · "Esqueci minha senha"** — fluxo de reset por e-mail (o SMTP já existe). Enquanto não houver,
      o caminho é pedir ao admin.
- [x] **P3 · Estados vazios orientados** — `VazioComponent` (ícone + título + ação) aplicado à lista de alunos; reutilizável nas demais.

### 🎨 Design
- [x] **P2 · Modal de confirmação própria** — feito (ver Usabilidade).
- [ ] **P3 · Refinos visuais** — favicon PNG real (hoje emoji inline), transições/hover consistentes,
      skeleton loading nas tabelas em vez de "Carregando...".
- [x] **P3 · Modo escuro** — variáveis semânticas + `prefers-color-scheme` (auto) e toggle manual (`data-tema`, persistido) no rodapé do menu.

### 📱 Compatibilidade mobile
- [x] **Retrocompatibilidade Safari/iOS antigo** — a build só mirava **iOS 18+** (default do Angular),
      deixando iPhones com iOS < 18 na **tela branca**. Adicionado `frontend/.browserslistrc` incluindo
      **Safari/iOS ≥ 14** (esbuild rebaixa a sintaxe) + **tela de fallback** no `index.html` (nunca fica
      branco; mostra "atualize o navegador" e captura o erro). Piso realista do Angular 17: Safari 15.
> O caso de uso nº 1 no celular é o professor **fazendo a chamada em sala**. Priorizar essa tela.
- [x] **P1 · Chamada otimizada para toque** — checkboxes nativos são pequenos para dedo; aumentar a área
      de toque (célula inteira clicável, alvos ≥44px) e testar a tabela de 5 colunas em 360px de largura.
- [x] **P2 · Responsividade das páginas** — `.tabela-cards`: no ≤600px as linhas viram cartão. Aplicado a **alunos** e à **chamada** (nome = cabeçalho; itens = linhas grandes de toque).
- [x] **P2 · Ícones PNG do PWA (192/512px)** — PNGs 192/512 + apple-touch-icon + favicon 32/48 no manifest e index.html.
- [ ] **P3 · Ajustes finos de PWA** — `safe-area-inset` para notch, splash screens iOS, atalhos de app
      (`shortcuts` no manifest, ex.: "Fazer chamada" direto).

## 🟡 Evoluções funcionais (curto prazo)

- [ ] **Dashboard com gráficos** (frequência ao longo do tempo, distribuição de presença).
- [ ] **Exportar relatório** de presenças para PDF/Excel.
- [ ] **Filtro por trimestre/período letivo** em relatórios e rankings (hoje já filtra por turma, falta o recorte de período).
- [ ] **Histórico da chamada** por aluno (linha do tempo de presença/itens).
- [x] **Batch de aniversário** — rotina agendada (`quarkus-scheduler`) que às **12:00 BRT** envia
      "feliz aniversário" a todos os alunos ativos com e-mail (ignora opt-in). Endpoint de teste
      `POST /api/admin/aniversarios/executar`. ⚠️ Instância única: sem recuperação de *misfire*
      (se a VM estiver fora do ar às 12h, os parabéns do dia não são reenviados).
- [x] **Relatório de visitantes** — por período, geral (todas as turmas, só ADMIN) ou por turma;
      exporta PDF/Excel. Endpoint `GET /api/relatorios/visitantes`.
- [x] **Boletim por trimestre** — notas das provas + frequência + situação; o aluno extrai o próprio
      (`GET /api/me/boletim`), ADMIN/PROFESSOR extraem de qualquer aluno (`GET /api/boletim`). PDF dedicado.
- [x] **E-mail de desempenho na prova** — botão "Lançar e notificar" envia a nota/aproveitamento ao
      aluno (respeita opt-in). `POST /api/provas/{id}/notas/notificar`.
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
- [x] **Deploy lento resolvido** — o build saiu da VM: as imagens são buildadas no CI e publicadas no
      **GHCR**; a VM só faz `pull` (deploy ~20 min → ~2 min, sem swap thrashing). Ver [`estagio1-ci-ghcr.md`](estagio1-ci-ghcr.md).
- [x] **Folga de RAM** — Postgres separado na 2ª VM (`ebd-db`); o backend ficou com `mem_limit` 700m.


### Segurança de migrations (evitar dor em produção)
- [x] **Backup do banco** — pós-split, roda **na `ebd-db`** por cron (`scripts/backup-ebd-db.sh`, valida e retém 14) + **offsite no OCI Object Storage** (PAR). Ver [`topologia.md`](topologia.md#backups). (O `backup-db.sh` no `cd.yml` era da topologia antiga.)
- [x] **CI rodando as migrations contra um Postgres real** — job `Migrations · app real (Postgres)` sobe o app contra um Postgres de serviço; falha se alguma migration quebrar ou o startup divergir do schema. Pega migration ruim **antes** de produção.
- [x] **Travar o `clean` do Flyway**: `quarkus.flyway.clean-disabled=true` no `application.properties` (nada zera o schema por engano).
- [ ] **Regra de rollback**: nunca voltar o app para uma versão anterior às migrations já aplicadas sem restaurar o backup do banco correspondente — documentar em [`migrations.md`](migrations.md).


- [ ] **Backups automáticos** do Postgres (cron + `pg_dump`).
- [x] **Chaves JWT persistentes** — via secrets no CD (`EBD_JWT_PRIVATE_KEY`/`EBD_JWT_PUBLIC_KEY`), gravadas no build a cada deploy; tokens sobrevivem. Ativar: adicionar os secrets (ver `docs/senhas-e-secrets.md`).
- [ ] **E-mail: conta Gmail dedicada para o envio** (migração Brevo → SMTP do Gmail em 2026-07,
      ver `docs/notificacoes-email.md`). Passos:
      1. criar um **Gmail gratuito** só do sistema (ex.: `ebd.ices@gmail.com`);
      2. **excluir a senha de app** criada no e-mail pessoal (`danilo.av@gmail.com` →
         myaccount.google.com/apppasswords → remover);
      3. criar a **senha de app na conta nova** e atualizar `EBD_MAIL_FROM`, `EBD_SMTP_USER`
         e `EBD_SMTP_PASS` no `.env` da VM + secret `OCI_ENV_FILE` + backup `.secrets-local/`.
- [ ] **Excluir a chave SMTP da Brevo** (não usamos mais o Brevo; a chave foi compartilhada
      em chat durante a config — apagar no painel: SMTP & API → API Keys).
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
