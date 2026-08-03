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
- [x] **P3 · Auditoria de ações** — tabela `auditoria` (migration V12) registra **quem** criou/alterou/excluiu
      **aluno, aula, prova e usuário** (usuário, data/hora, ação, entidade, id e rótulo). `AuditoriaService`
      grava dentro da transação da própria operação; tela admin `/auditoria` (só ADMIN) com filtros por
      entidade/período. Testes em `AuditoriaServiceTest`.
- [ ] **Vulnerabilidades do Dependabot (deps do frontend) — 9 high (2026-08)** — o `npm audit` acusa:
      (1) **`@angular/core` — 6 advisories de XSS** (i18n, SVG, sanitization/namespace bypass, hydration)
      que só têm patch em **majors suportados** → dependem do **upgrade do Angular** (ver Compatibilidade
      mobile). Impacto real menor neste app: **não** usamos `@angular/localize` nem SSR/hydration e não há
      `bypassSecurityTrust`/`DomSanitizer` no código — sobram os *sanitization bypass* genéricos, num app
      interno e autenticado. (2) **`xlsx` (SheetJS) — Prototype Pollution + ReDoS**, **sem fix no npm** (a
      SheetJS saiu do registry): migrar para **`exceljs`** ou instalar do **CDN oficial** da SheetJS.
      **Adiado** junto com o upgrade do Angular; reavaliar assim que o `/uso` indicar baixa exposição a
      iOS antigo. Repo privado, então o risco de exposição é contido.
- [ ] **P3 · Token em localStorage** — acessível a XSS (Angular escapa por padrão; risco baixo). Alternativa
      robusta (cookie httpOnly + CSRF) só se o app crescer.
- [ ] **P3 · "Entrar como" com restrição no backend** — hoje o alternador de perfil "Atuando como"
      (Gestão ↔ Aluno) é **só foco de UI**: o JWT carrega **todos** os papéis do usuário, então um
      usuário com múltiplos papéis consegue, por **URL direta**, acessar telas do outro perfil (ele
      tem a permissão de fato). Se um dia for preciso um verdadeiro "entrar como" que **restrinja no
      servidor**, seria emitir/derivar o token pelo perfil ativo (ou um claim de escopo) e checar isso
      nos endpoints — passo a mais, hoje fora de escopo. Ver [`unificacao-papeis.md`](unificacao-papeis.md).

### 🧭 Usabilidade
- [x] **P1 · Aviso de sessão expirada** — o 401 redireciona ao login em silêncio; mostrar toast
      "Sua sessão expirou, entre novamente" (o interceptor já centraliza isso).
- [x] **P2 · Substituir `confirm()` nativo** — `ConfirmService` + `ConfirmDialogComponent` (modal no padrão do app) substituem os 8 `confirm()` nativos.
- [x] **P2 · Busca/filtro na lista de alunos** — campo de busca por nome (acento-insensível) na tela de alunos.
- [x] **P3 · "Esqueci minha senha/usuário"** — link no login → informar o e-mail → recebe o **usuário** + um **link com token** (uso único, expira em 60 min) para criar nova senha. Resposta genérica (anti-enumeração). Migration **V21** (`reset_senha`), endpoints públicos em `/api/auth` e telas `/recuperar` e `/redefinir`.
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
- [ ] **Upgrade do Angular 17 → major suportado (adiado — decisão baseada em dado)** — as
      vulnerabilidades do Dependabot (ver Segurança) só têm correção em majors **ainda suportados** do
      Angular; ficar no 17 é insustentável a médio prazo. **Adiado de propósito** para não arriscar o
      engajamento no lançamento (2026-08). Quando for: subir **major por major** com `ng update`
      (17→18→19…), **mantendo o `.browserslistrc`** (Safari/iOS 14) e **testando num iPhone antigo a
      cada passo** — o risco não é a sintaxe (o esbuild rebaixa), é o **runtime do framework** elevar o
      piso de Safari/iOS. Exige **subir o Node** (18.19+/20; build-time, não afeta o device). O card
      **Dispositivos** do painel `/uso` passa a **medir a exposição real a iOS antigo**, para escolher o
      alvo do salto com número, não achismo. **Não** rodar `npm audit fix --force` (pula direto pro
      major mais novo — pior caminho p/ device antigo).
> O caso de uso nº 1 no celular é o professor **fazendo a chamada em sala**. Priorizar essa tela.
- [x] **P1 · Chamada otimizada para toque** — checkboxes nativos são pequenos para dedo; aumentar a área
      de toque (célula inteira clicável, alvos ≥44px) e testar a tabela de 5 colunas em 360px de largura.
- [x] **P2 · Responsividade das páginas** — `.tabela-cards`: no ≤600px as linhas viram cartão. Aplicado a **alunos** e à **chamada** (nome = cabeçalho; itens = linhas grandes de toque).
- [x] **P2 · Ícones PNG do PWA (192/512px)** — PNGs 192/512 + apple-touch-icon + favicon 32/48 no manifest e index.html.
- [x] **Menu em gaveta no celular** — a barra lateral vira **off-canvas drawer** (desliza da esquerda,
      com backdrop e barra de topo fixa com ☰); tocar num item/fora/✕ fecha, com trava de rolagem. Router
      com scroll-to-top ao navegar. Antes o menu empilhava no topo e a página abria "abaixo". Desktop igual.
- [ ] **P3 · Ajustes finos de PWA** — `safe-area-inset` para notch, splash screens iOS, atalhos de app
      (`shortcuts` no manifest, ex.: "Fazer chamada" direto).
- [ ] **P3 · Refinos mobile** — gestos de swipe p/ a gaveta, revisão de tap-targets nas telas internas,
      unificar breakpoints (tabelas-cartão a 600px vs. menu a 820px).

## 🟡 Evoluções funcionais (curto prazo)

- [x] **Dashboard com gráficos** — painel com presença média + gráfico de barras de **frequência por aula** (com meta 75%) e barra de **distribuição por faixa** (Excelente/Boa/Atenção). SVG customizado (sem dependência, dark-mode). Endpoint `GET /api/dashboard`.
- [ ] **Exportar relatório** de presenças para PDF/Excel.
- [x] **Filtro por trimestre/período letivo** em relatórios e rankings — rankings ganharam recorte por trimestre (`GET /api/desafios/rankings?ano=&trimestre=`, seletor na tela); o relatório de presenças (que já aceitava início/fim) ganhou um **atalho de trimestre** que preenche as datas. Lógica de datas centralizada em `PeriodoLetivo`.
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
- [x] **Quiz / prova online (auto-corrigida)** — a prova ganhou `tipo` OFFLINE/ONLINE; o professor monta
      questões (múltipla escolha + V/F) e o aluno responde em `/minhas-provas` (1 tentativa + janela). A
      correção grava `NotaProva` (→ boletim, rankings, e-mail de nota). Migration V10. Refinos pendentes:
    - [ ] **Questões dissertativas** (resposta em texto, com correção manual pelo professor).
    - [ ] **Cronômetro / tempo limite** por prova (encerra e envia automaticamente ao esgotar).
    - [ ] **Embaralhar alternativas** (e questões) por aluno, para dificultar a cola.
    - [ ] **Banco de questões reutilizável** (montar provas a partir de questões salvas/tags).
- [x] **Acesso automático do aluno** — todo aluno cadastrado ganha um **login** (usuário ALUNO vinculado),
      com **senha padrão `12345678`** e **troca obrigatória no 1º acesso** (`precisa_trocar_senha`,
      migration V11). Login = `nome.sobrenome` (sem acento, sufixo em colisão); backfill idempotente no
      boot para os já cadastrados. Tela de alunos mostra a coluna **Login**.
- [x] **Login editável + validações** — o login pode ser trocado no cadastro do Aluno e na tela de
      Usuários, com regras num único ponto (`LoginService`): normaliza (minúsculas), formato
      `[a-z0-9]`+`. - _` (3–60, sem espaço/acento) e unicidade.
- [x] **"Esqueci minha senha" / reset do aluno** — feito via link+token (ver item P3 em Segurança). Serve para qualquer usuário com e-mail cadastrado, inclusive o aluno.
- [ ] **Observações por aula/aluno** (campo de texto livre na chamada).
- [ ] **Copiar a chave PIX na requisição** — na tela da requisição já aberta, o campo `pix_chave` deve ter um botão **"copiar"** (ícone) que joga a chave no clipboard, para o tesoureiro colar direto no app do banco sem digitar. Só front (Clipboard API + toast de confirmação); sem backend.
- [x] **Falta justificada do aluno + regras de status automático (2026-07-28)** — três entregas na mesma frente (migration **V24**: `justificada`/`justificativa_motivo`/`justificada_em` em `presenca`):
  1. **Justificar falta (autosserviço, 30%)** — o aluno justifica suas faltas na própria área (`/minha-frequencia`, modal com motivo); vale na hora, sem aprovação. Uma falta justificada passa a valer **0,3** (30% de uma presença) no ranking *menos faltou* e no componente de presença da *classificação geral* (`DesafiosService`). Endpoints `POST/DELETE /api/me/frequencia/{aulaId}/justificar`.
  2. **Inativação por faltas seguidas** — ao salvar a chamada, aluno com **mais de 4 faltas consecutivas sem justificativa** (5ª seguida) é **inativado** automaticamente (uma presença OU falta justificada zera a sequência). Registrado na Auditoria e devolvido como alerta (toast) a quem fez a chamada.
  3. **Visitante vira aluno** — ao cadastrar visitante, quem compareceu às **3 aulas mais recentes seguidas** da turma (identidade = nome + telefone/e-mail) é promovido a **aluno** (com login automático). Registrado na Auditoria + alerta no cadastro.
  Testes: `FaltaJustificadaEInativacaoTest` (5 casos), suíte com **50** testes verdes; `ng build` OK. Os dois eventos automáticos também disparam e-mail (aviso ao aluno inativado; boas-vindas ao visitante promovido) pelo `NotificacaoService` (toggle `ebd.notificacoes.enabled`).

### 📊 Estatísticas de uso / engajamento (A–G)

Telemetria de uso do app (quem acessa, quando, quanto), complementar aos rankings de domínio.
Base já entregue no **quick win**; os demais itens (A–G) ficam neste backlog priorizado.

- [x] **Quick win — painel `/uso` (ADMIN) + instrumentação de acesso (2026-08-02)** — migration **V25**
      (`acesso_evento` + `usuario.ultimo_acesso`); o login grava um evento (com `user_agent`) e atualiza o
      last-seen; **ping** leve (`PUT /api/me/ping`, a cada ~60s no shell) mantém o "online agora". Endpoint
      `GET /api/uso` (`UsoService`) + página `/uso` (KPIs, gráficos em CSS, sem libs). Cobre, no todo ou em
      parte, os itens **A, B, C, E (parcial), F (parcial) e G**. Validado: `mvn package` e `ng build`.
- **A. Tempo real — "quem está online"** — 🟡 parte entregue (online agora via last-seen/ping).
  - [x] Online agora (últimos 15 min) + lista (nome/papel).
  - [ ] Pico de simultâneos (hoje/histórico) e "ao vivo na aula" (online no horário da EBD de domingo).
- **B. Volume de acesso** — 🟢 entregue.
  - [x] Acessos hoje/7d/30d e **DAU/WAU/MAU** (pessoas únicas ativas).
  - [x] Série diária (14 dias); distribuição por **hora do dia** e **dia da semana** (30 dias).
- **C. Adoção / ativação** — 🟢 entregue (núcleo).
  - [x] Taxa de ativação (contas e alunos que já acessaram); contas que nunca acessaram.
  - [ ] Funil completo (cadastrado → 1º login → trocou senha → usou 1 feature) e retenção por coorte (sem 1→2).
- **D. Uso por funcionalidade** — 🔴 pendente (precisa instrumentar eventos de página/feature).
  - [ ] Feature mais usada (chamada, frequência, quiz, boletim, desafios, requisições); exports e cliques
        (ex.: "Parabenizar no WhatsApp"). Quiz respondido e requisições já são deriváveis do domínio.
- **E. Engajamento & retenção do aluno** — 🟡 parte entregue.
  - [x] Mais ativos (30 dias) e **dormentes** (login, mas 14+ dias sem acessar).
  - [ ] Streak de semanas seguidas e % que abre fora do domingo.
- **F. Professores / gestão** — 🟡 parte derivável.
  - [ ] Professores mais ativos (via `auditoria`); chamada no prazo × atrasada; cobertura de turmas na semana.
- **G. Dispositivos / técnico** — 🟢 entregue (núcleo) — **alimenta a decisão do upgrade do Angular**.
  - [x] Classificação por dispositivo/SO (iPhone/Android/Mac/Windows…) a partir do `user_agent` — mede a
        exposição real a **Safari/iOS antigo** antes de decidir o salto de major (ver seção Compatibilidade mobile).
  - [ ] Versão exata de iOS/navegador, mobile × desktop e PWA instalado × navegador.


## 🟢 Qualidade e robustez

- [x] **Testes automatizados (backend)** — 23 `@QuarkusTest` cobrindo login/JWT + proteção de rota, `ChamadaService` (upsert), `ProvaService` (validação de nota), `RelatorioService` (agregação), `DesafiosService` (rankings), `BoletimService` (trimestre), `NotificacaoService`/campanha (e-mail via `MockMailbox`), **auto-correção do quiz** (`QuizAlunoService`) e **acesso/login do aluno** (`AcessoAlunoService`: geração, colisão, edição de login). Rodam contra Postgres real (`ebd_test`) local e no CI (`mvn verify` com serviço Postgres).
- [x] **Correção — aulas futuras não contam** — ranking e boletim passaram a considerar só aulas com
      **data ≤ hoje** (total de aulas, presenças e visitantes); antes inflavam as faltas do trimestre corrente.
- [ ] **Testes de front** (ao menos smoke dos serviços/guards).
- [x] **Envio de e-mail assíncrono** — `EmailDispatcher` publica o `Mail` no **Vert.x EventBus** e um consumidor `@ConsumeEvent @Blocking` envia em segundo plano (worker thread). A resposta da chamada não espera mais o SMTP e os e-mails saem fora da transação; dedup/contagem seguem síncronas. Testes de mailbox com Awaitility.
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
- [x] **⚠️ Pré-requisito p/ tornar público — histórico purgado (2026-07)** — a chave JWT privada (`privateKey.pem`) estava no **histórico** (commit `e2c35e9`, removida em `7a5285a`, mas o blob permanece). Antes de abrir: (a) **purgar o histórico** (git filter-repo/BFG removendo os `*.pem`) **ou** confirmar que a chave foi **rotacionada em prod** (o CLAUDE.md diz que sim → exposição de baixo impacto, mas o ideal é purgar); (b) rodar **gitleaks no histórico inteiro** (`gitleaks detect`, sem `--no-git`) para pegar qualquer outro segredo (ex.: verificar a chave SMTP da Brevo); (c) ativar **secret scanning + push protection** ao abrir. Os secrets do repo (OCI_*, EBD_JWT_*, EBD_GHCR_*) **não** estão no código/histórico (são secrets criptografados) — abrir o repo não os expõe. **Feito:** `git filter-repo --path-glob '*.pem' --invert-paths` + force-push (develop/main/tags reescritos, sem `.pem`; backup do estado anterior guardado). **Ressalva:** o GitHub mantém `refs/pull/*/head` apontando pros commits antigos (não dá pra reescrever — 'hidden ref'), então o blob antigo só some quando o GitHub faz GC; mitigação = a chave **já foi rotacionada** (baixo impacto) + opcional abrir chamado no Suporte. **Falta antes de abrir:** rodar gitleaks no histórico inteiro e confirmar a chave de prod nova.
- [ ] **Budget/alerta de custo** na OCI (garantia extra contra cobrança).
- [ ] **(adiado — custo) Runner self-hosted p/ o CI/CD** (repo privado gasta minutos do GitHub Actions; o build já é pesado). Runner Docker numa VM OCI dedicada — script `scripts/setup-runner-oci.sh` + runbook [`self-hosted-runner.md`](self-hosted-runner.md). Pré-requisitos: VM com RAM p/ buildar (A1 arm64 no free) e decidir arm64 x x86 do app. Alternativa mais robusta (autoscaling): ARC no OKE.
- [ ] **Tornar o repo PÚBLICO** (decisão 2026-07) — resolve o billing do Actions (repo público tem minutos **grátis/ilimitados**) e **destrava a proteção de branch de graça** (hoje exigiria GitHub Pro). **Só depois** do pré-requisito de segurança acima (segredos no histórico). Ao abrir, o runner self-hosted **não** pode ser usado (RCE via PR de fork) — por isso ele fica adiado mesmo.
- [ ] **Proteção de branch na `main`** (bloquear push direto, exigir PR + CI verde) — **fica grátis** assim que o repo virar público. Aplicar via `gh api PUT .../branches/main/protection`.
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
- **Ainda em aberto:** **outros canais** (Telegram grátis / WhatsApp). _(Envio assíncrono: concluído — ver Qualidade.)_
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
- [ ] **Alertas de faltas seguidas por e-mail** (ex.: 3 ausências consecutivas → e-mail especial ao aluno/coordenação)
      e **aniversários** — reaproveita o `NotificacaoService`. _A regra de **inativação** por 5 faltas seguidas já existe (V24); e o aviso por e-mail ao aluno inativado já existe (V24); falta só o e-mail de "3 ausências" preventivo e o de aniversário._
- [ ] **Premiação automática** ao fim do trimestre com base nos rankings.
- [ ] **Domínio próprio + DKIM** (sair do DuckDNS, que não permite DKIM) para o `no-reply@` entregável.

## Limitações conhecidas (estado atual)

- Exclusão de aluno/aula/prova é **destrutiva** (cascata).
- ~~Rankings/relatórios não recortavam por trimestre/período~~ — **resolvido**: recorte por trimestre em ambos (ver Evoluções funcionais).
- ~~E-mail de chamada é síncrono na transação~~ — **resolvido**: envio assíncrono via EventBus (ver Qualidade).
- Textos dos e-mails (presente/ausente) são **fixos no código** — ainda não configuráveis pela UI.
- O alternador de perfil "Atuando como" é **só de UI** — o token carrega todos os papéis, então um
  usuário multi-papel pode acessar telas do outro perfil por URL direta (tem a permissão). Um "entrar
  como" que restrinja no backend está no backlog (Segurança, P3).
- Sem testes automatizados; validação por build (`mvn package` / `ng build`) e smoke manual.
- Uma única VM (1 GB) roda tudo (db + api + front + caddy) — suficiente para o MVP, não para alta disponibilidade.
