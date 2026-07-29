# CLAUDE.md — Contexto do projeto para o Claude Code

> Este arquivo é lido automaticamente no início de cada sessão. Ele resume **o que é o
> projeto, como rodar, como está organizado e o que já foi decidido**, para que qualquer
> nova sessão comece com contexto completo. Mantenha-o atualizado ao evoluir a aplicação.

## 1. O que é

Hotsite para gestão da **Escola Bíblica Dominical (EBD) — classe de adultos** da
**Igreja Cristã Evangélica em Samambaia (ICE)**. Dono/desenvolvedor: **Danilo** (`danilo.av@gmail.com`).

Dois módulos no MVP:

- **Chamada** — controle de presença por aula, avaliando 4 itens por aluno: *presente*,
  *trouxe a Bíblia*, *trouxe a revista*, *estudou a lição*. Inclui CRUD de alunos e
  **relatório de presenças** por período.
- **Desafios** — rankings de premiação derivados da chamada + notas: *menos faltou*,
  *mais trouxe Bíblia*, *mais trouxe revista*, *mais estudou a lição*, *melhores notas*.
  Tem o submódulo **Provas** (CRUD de provas + lançamento de notas por aluno).

Idioma do domínio/código: **português** (nomes de classes, variáveis, rotas, UI).

## 2. Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java **17** · **Quarkus 3.15** · Hibernate ORM **Panache** · **Flyway** · JWT (smallrye) com perfis |
| Frontend | **Angular 17** standalone (sem Angular Material) · SCSS · Signals |
| Banco | **PostgreSQL 16** |
| Infra | Docker Compose · **2 VMs OCI** (app+banco) · Caddy (HTTPS) + nginx · imagens no GHCR |
| Cloud | Oracle Cloud (OCI) — VM Always Free (A1.Flex ARM), região `sa-saopaulo-1` |

Ambiente local do Danilo: Java 19 (roda o target 17), Maven 3.9, **Node 18.13** (por isso
Angular **17**, não 18+), Docker. Shell: **zsh** (atenção: scripts usam shebang bash).

## 3. Mapa do repositório

```
claude-trabalho/
├── CLAUDE.md                      ← este arquivo
├── README.md                      ← visão geral + como rodar
├── CHANGELOG.md                   ← histórico de versões (releases; gerado no CD)
├── docker-compose.yml             ← stack all-in-one (dev/local; fallback — prod usa os 2 abaixo)
├── docker-compose.app.yml         ← PROD VM app: caddy+frontend+backend (imagens do GHCR)
├── docker-compose.db.yml          ← PROD VM banco (ebd-db): só Postgres
├── docker-compose.dev.yml         ← só Postgres (dev)
├── .env.example                   ← credenciais do compose de produção
├── backend/                       ← API Quarkus (pacote br.com.ice.ebd)
│   ├── pom.xml
│   ├── Dockerfile                 ← multi-stage (imagem publicada no GHCR; chaves JWT vêm do volume /keys em prod)
│   └── src/main/
│       ├── java/br/com/ice/ebd/
│       │   ├── model/             ← entidades JPA (Aluno, Aula, Presenca, Prova, NotaProva, Usuario, Role)
│       │   ├── repository/        ← Panache repositories
│       │   ├── service/           ← regra de negócio (Auth, Aluno, Aula, Chamada, Relatorio, Prova, Desafios, Notificacao)
│       │   ├── resource/          ← endpoints REST (JAX-RS) + ErrorMapper + MeResource
│       │   ├── dto/               ← records de request/response
│       │   ├── security/          ← TokenService (emite JWT)
│       │   └── bootstrap/         ← DataInitializer (seed de usuários e alunos de exemplo)
│       └── resources/
│           ├── application.properties
│           ├── db/migration/V1__schema.sql   ← schema (Flyway)
│           └── (privateKey.pem/publicKey.pem — NÃO versionados)
├── frontend/                      ← Angular 17
│   ├── Dockerfile                 ← build node → nginx
│   ├── nginx.conf                 ← SPA + proxy /api → backend:8080
│   └── src/app/
│       ├── core/                  ← auth.service, api.service, guards, auth.interceptor, toast.service, models.ts
│       ├── layout/shell.component.ts   ← menu lateral + outlet
│       └── pages/                 ← login, dashboard, alunos, chamada, relatorio, provas, notas, desafios
├── Caddyfile                      ← HTTPS (Let's Encrypt) + proxy → frontend (VM app)
├── .github/workflows/             ← CI (build + SAST/segurança) e CD (build imagens GHCR + pull na VM)
├── scripts/                       ← automação OCI + deploy + backup (ver seção 8)
└── docs/
    ├── topologia.md               ← INFRA ATUAL: 2 VMs (app+banco) + GHCR + backups (LER p/ deploy/infra)
    ├── ARQUITETURA.md             ← modelo de dados, camadas, decisões (LER para mudanças estruturais)
    ├── REGRAS-DE-NEGOCIO.md       ← catálogo das regras de negócio (ranking, inativação, boletim, tesouraria...)
    ├── migrations.md              ← changelog das migrations (Flyway) + como recriar o banco
    ├── API.md                     ← referência de endpoints com exemplos
    ├── ROADMAP.md                 ← backlog e limitações conhecidas
    ├── rodar-local.md             ← como rodar localmente (dev, passo a passo)
    ├── CICD.md                    ← esteira GitHub Actions (CI segurança + CD OCI) + fluxo de branches
    ├── producao.md                ← produção: URL/IP, SSH, operação
    ├── senhas-e-secrets.md        ← onde ficam senhas/secrets e como consultar
    ├── pos-vm.md                  ← runbook: deploy quando a VM subir (bootstrap + secrets)
    ├── deploy-oracle.md           ← passo a passo de deploy na VM
    ├── integracao-tesouraria.md   ← runbook: acesso read-only externo à tesouraria (view V17 + túnel SSH)
    ├── unificacao-papeis.md       ← papéis como capacidades (flags) + consolidação de contas duplicadas
    ├── self-hosted-runner.md      ← runner self-hosted (Docker numa VM OCI) p/ economizar minutos do Actions
    └── consolidacao-contas.md     ← runbook: consolidar contas duplicadas + excluir usuário com requisições (SQL prod)
```

## 4. Como rodar (dev)

```bash
# 1) Banco
docker compose -f docker-compose.dev.yml up -d

# 2) Chaves JWT (só na 1ª vez) + backend em :8080
./scripts/gen-jwt-keys.sh
cd backend && mvn quarkus:dev        # Swagger: http://localhost:8080/q/swagger-ui

# 3) Frontend em :4200
cd frontend && npm install && npm start
```

Login inicial: **`admin` / `admin123`** (ADMIN) ou **`professor` / `prof123`** (PROFESSOR).
Esses usuários são criados no 1º boot pelo `DataInitializer` (troque as senhas em produção).

## 5. Como validar mudanças

- **Backend**: `cd backend && mvn -q -B package -DskipTests` — a *augmentation* do Quarkus
  valida CDI, config, segurança e endpoints (é a verificação mais forte sem subir banco).
- **Frontend**: `cd frontend && npx ng build` — compila todo o TS/templates.
- **Runtime completo**: `docker compose up -d --build` e acessar `http://localhost`.

## 6. Segurança / perfis

- Login retorna **JWT** (RS256, chaves em `backend/src/main/resources/*.pem`).
- **ADMIN**: CRUD de alunos e provas, excluir aulas, tudo o mais.
- **PROFESSOR**: fazer chamada, criar aulas, lançar notas, ver relatórios/rankings, consultar alunos.
- Rotas de escrita de alunos/provas exigem `@RolesAllowed("ADMIN")`; leitura e operações de
  chamada/notas são `ADMIN` + `PROFESSOR`.
- **Segredos NÃO versionados**: chaves JWT (`*.pem`) e `scripts/.oci-launch.env` estão no
  `.gitignore`. O `backend/Dockerfile` gera chaves novas no build se não existirem.
  ⚠️ A chave JWT antiga ainda está no **histórico** do Git (repo privado; regenerada em prod).

## 7. Convenções

- **Idioma**: tudo em português (classes, métodos, rotas, mensagens, UI).
- **Backend**: camadas `resource → service → repository`; DTOs são `records`; entidades nunca
  são expostas direto na API (sempre mapeadas para DTO). Erros de negócio via
  `WebApplicationException` (tratados pelo `ErrorMapper` → JSON `{message,status}`).
- **Frontend**: componentes **standalone**, estado com **signals**, um único `ApiService`
  para toda a API, feedback via `ToastService`. Sem Angular Material (SCSS custom em `styles.scss`).
- **Banco**: schema é dono do **Flyway** (`hibernate.database.generation=none`). Toda mudança de
  modelo exige **nova migration** `V2__...`, `V3__...` (não editar a V1 já aplicada).

## 8. Deploy na OCI (topologia de 2 VMs + GHCR)

> Referência autoritativa da infra: [`docs/topologia.md`](docs/topologia.md).

- **Região** `sa-saopaulo-1`, subnet pública única. **2 VMs Always Free** E2.1.Micro (1 OCPU / 1 GB),
  Ubuntu 22.04 + swap, custo US$ 0:
  - **`ebd-server`** (app · 163.176.181.38 / **10.0.1.45**): caddy + frontend + backend
    (`~/ebd-samambaia`, `docker-compose.app.yml`).
  - **`ebd-db`** (banco · 136.248.80.0 / **10.0.1.54**): Postgres (`~/ebd-db`, `docker-compose.db.yml`),
    bind no IP privado; 5432 liberada só de `10.0.1.45/32` (Security List + iptables).
- **Deploy**: merge na `main` → **CD** builda `ebd-backend`/`ebd-frontend` e publica no **GHCR privado**
  (`ghcr.io/daniloav/ebd-*`); a `ebd-server` faz `docker login` + `pull` + `up` (**sem build na VM**, ~2 min).
  Chaves JWT montadas em runtime (volume `./keys`). Rollback: `EBD_IMAGE_TAG=<sha>` no `.env`.
- **Scripts** (em `scripts/`):
  - `oci-a1-retry.sh` (cria VM, agnóstico ao shape) · `oci-descobrir.sh` · `oci-bootstrap.sh` (Docker+portas na VM).
  - `estagio2-cutover.sh` — split do banco na 2ª VM (roda do Mac). `gen-jwt-keys.sh` — chaves de dev.
  - `backup-ebd-db.sh` (roda na ebd-db, via cron) + `setup-backup-ebd-db.sh` + `setup-offsite-oci.sh` (offsite no Object Storage).
  - `.oci-launch.env` (ignorado) — OCIDs reais; modelo em `.oci-launch.env.example`.
- **CI/CD** (ver [`docs/CICD.md`](docs/CICD.md)): `ci.yml` (build + Semgrep/Trivy/gitleaks); `codeql.yml`
  (pulado em repo privado); `cd.yml` (build imagens → GHCR → pull na VM). Secrets: `OCI_*`, `EBD_JWT_*`, `EBD_GHCR_*`.
- **Backups**: diário na `ebd-db` (local rotacionado) + offsite no **OCI Object Storage** (bucket `ebd-backups`, PAR write-only).
- Runbooks: [`docs/estagio1-ci-ghcr.md`](docs/estagio1-ci-ghcr.md) · [`docs/estagio2-db-separado.md`](docs/estagio2-db-separado.md) · [`docs/deploy-oracle.md`](docs/deploy-oracle.md).

## 9. Estado do projeto (atualizar aqui a cada avanço)

- 🏗️ **Topologia de 2 VMs + GHCR (2026-07)** — Postgres separado na `ebd-db` (backend com folga de RAM);
  imagens buildadas no CI e publicadas no GHCR privado, a VM só faz `pull` (deploy ~2 min); e-mail **assíncrono** para todas as notificações (EventBus — ver bullet abaixo); backup diário na `ebd-db` + offsite no Object Storage. Ver [`docs/topologia.md`](docs/topologia.md).
- ✅ MVP completo (backend + frontend + infra + scripts + docs).
- ✅ Backend passa no `mvn package`; frontend passa no `ng build`.
- ✅ Código no GitHub: `git@github.com:daniloav/ebd-samambaia.git` (repo **privado**), branch `main`.
- ✅ Segredos removidos do versionamento. CI/CD verde (CD em mock até secrets reais).
- ✅ **Segurança P1+P2** — CORS fechado, anti-força-bruta no login, headers no Caddy, aviso de sessão
  expirada, chamada por toque (P1); senha mínima (8), trocar a própria senha (`PUT /api/me/senha` +
  tela *Minha conta*) e chaves JWT persistentes opt-in via secrets `EBD_JWT_*` (P2). Ver `docs/ROADMAP.md`.
- ✅ **Runtime VALIDADO (2026-07-19)** — smoke test ponta-a-ponta OK: Flyway migrou, seed criou
  usuários/alunos, login JWT, chamada (upsert), provas+notas, rankings e relatório responderam
  contra Postgres real; login pela UI navega ao painel com dados reais.
- 🐳 **Docker local não funciona neste Mac** (daemon trava — `docker ps` pendura). Para dev local,
  use **Postgres nativo** em vez do `docker-compose.dev.yml`:
  `brew services start postgresql@16` + role/db `ebd` (senha `ebd`) na porta 5432.
  Na VM (deploy) o Docker é limpo e funciona normalmente.
- 📊 **Relatório de visitantes + batch de aniversário + evolução de provas** — relatório de visitantes
  por período (geral/turma, com export PDF/Excel); 1ª rotina batch (`quarkus-scheduler`) que às 12:00 BRT
  manda "feliz aniversário" a todos com e-mail; provas ganharam botão "Lançar e notificar" (e-mail de nota)
  e **boletim por trimestre** (notas+frequência) que o aluno extrai em PDF. Sem migration.
- 📧 **Alertas por e-mail** (roadmap item 2): ao salvar a chamada, envia e-mail aos alunos com opt-in
  (`recebe_notificacoes` + `email`, migration V4). Toggle `ebd.notificacoes.enabled` (off em prod até
  configurar SMTP). Validado em dev com mailer *mock*. Guia: [`docs/notificacoes-email.md`](docs/notificacoes-email.md).
- 🧠 **Quiz / prova online (auto-corrigida)** — a prova ganhou um `tipo`: **OFFLINE** (atual, nota à
  mão) ou **ONLINE** (quiz respondido pela tela e corrigido na hora). Migration **V10** (questao,
  alternativa, submissao, resposta + `tipo`/`abre_em`/`fecha_em` na prova). Professor monta as questões
  (múltipla escolha e V/F, 1 correta) em `/provas/:id/questoes`; a nota máxima vira a soma dos pontos.
  Aluno responde em **`/minhas-provas`** (1 tentativa + janela opcional): a correção grava `NotaProva`
  (→ boletim, rankings e e-mail de nota) e mostra o gabarito. Endpoints do aluno em `/api/me/provas*`.
  Validado: `mvn test` (2 testes de auto-correção, 11 no total), `ng build`, e ponta-a-ponta em dev
  (montar → responder → nota 6/10 → 2ª tentativa bloqueada → boletim). Branch `feature/quiz-prova-online`.
- 🔑 **Acesso automático do aluno** — todo aluno cadastrado passa a ter um **login** (usuário ALUNO
  vinculado) criado na hora, com **senha padrão `12345678`** e **troca obrigatória no 1º acesso**
  (`precisa_trocar_senha`, migration **V11**). Login = `nome.sobrenome` (sem acento, sufixo numérico em
  colisão). `AcessoAlunoService` cuida disso (criar aluno, backfill idempotente no boot p/ os existentes,
  espelha ativo/e-mail, remove o login ao excluir o aluno). Login retorna `precisaTrocarSenha`; o front
  prende o usuário em `/conta` (guard) até trocar. Tela de alunos mostra a coluna **Login**. Testes:
  `AcessoAlunoServiceTest` (login gerado, colisão, idempotência). Branch `feature/acesso-aluno-automatico`
  (empilhada sobre a do quiz — a V11 vem após a V10).
- ✏️ **Login editável (nome.sobrenome pode ser trocado)** — o login (username) agora pode ser editado
  no cadastro do **Aluno** (campo "Login de acesso") e na tela de **Usuários**, com regras num único
  ponto (`LoginService`): normaliza (trim+minúsculas), valida formato (`[a-z0-9]` + `. - _`, 3–60, sem
  espaço/acento) e unicidade. `AcessoAlunoService.definirLogin` renomeia o login do aluno (idempotente).
  Branch `feature/editar-login-usuario`. Testes em `AcessoAlunoServiceTest` (23 no total).
- 💰 **Módulo de Requisições da Tesouraria** — tesoureiro e líder são **capacidades (flags)** que qualquer usuário acumula sobre a role base (migration **V18**: `eh_tesoureiro`/`eh_lider` em `usuario`; role base volta a `ADMIN/PROFESSOR/ALUNO`). Um professor ou aluno pode ser tesoureiro/líder; o **ADMIN** recebe as duas por padrão. O JWT (TokenService) emite os grupos `TESOUREIRO`/`LIDER` conforme as flags, então os `@RolesAllowed` seguem iguais. **LIDER** abre pedidos, **TESOUREIRO** avalia. **Forma de repasse (V20):** na abertura o líder escolhe **dinheiro ou PIX** (chave CPF/e-mail/telefone, nunca aleatória, e validada como sendo do próprio solicitante — e-mail confere com o login, telefone com o aluno vinculado); na aprovação o tesoureiro pode anexar o **comprovante de transferência** (anexo com `categoria` NOTA_FISCAL x COMPROVANTE). A view de integração ganhou forma/pix + `possui_comprovante`. Líder solicita recurso (ministério, evento, destinação, motivo, valor, data) → nº único `REQ-<ano>-<seq4>`; tesoureiro **aprova** (define valor aprovado + parecer) ou **nega**; aprovada fica pendente de **prestação de contas** até anexar a(s) **nota(s) fiscal(is)** (PDF/imagens, multipart em `bytea`) + valor gasto → FINALIZADA. **Cobrança diária por e-mail** (`CobrancaNotaService`, `@Scheduled` BRT, dedup por dia). Mensageria: nova→tesoureiros, avaliação→solicitante, finalização→tesoureiros (toggle `ebd.notificacoes.enabled`). Migration **V16** (+ ajuste do `ck_usuario_role` p/ os 2 papéis). Front: página `/requisicoes` adaptada ao papel (badges/filtro/modais + download de anexo), `tesourariaGuard`, grupo **Tesouraria** no menu, perfis Tesoureiro/Líder em Usuários. Validado: `mvn package`, `ng build`, suite (34 testes, `RequisicaoFluxoTest`) e fluxo por API. Branch `feature/tesouraria-requisicoes` (pushed, aguardando PR). **Integração externa** (mesma branch): view read-only `vw_requisicoes_integracao` (migration **V17**) para o sistema do tesoureiro consumir por SELECT — runbook completo em [`docs/integracao-tesouraria.md`](docs/integracao-tesouraria.md) (usuário `tesouraria_ro` least-privilege, senha gerada pelo operador, acesso via **túnel SSH** recomendado ou bind público + allowlist de IP) e script `scripts/setup-integracao-tesouraria.sh` que faz banco + túnel de uma vez.
- 📨 **Envio de e-mail assíncrono (EventBus)** — todo `mailer.send()` passou a ser desacoplado do fluxo da requisição: o `NotificacaoService` publica o `Mail` no Vert.x EventBus (`EmailDispatcher.enfileirar`) e um consumidor `@ConsumeEvent @Blocking` envia em segundo plano (worker thread). A resposta HTTP da chamada não espera mais o SMTP, e os e-mails da tesouraria saem **fora** da transação do banco. A **dedup e a contagem seguem síncronas** (só banco), então "X e-mails disparados" continua válido — "disparado" agora = enfileirado (falha de SMTP em background só loga, não re-tenta). Testes de mailbox usam Awaitility. Validado: 34 testes, `mvn package`, e no dev (criar requisição responde em ~0,1 s). Branch `feature/tesouraria-requisicoes`.
- 🧑‍🤝‍🧑 **Papéis unificados (capacidades)** — ADMIN/PROFESSOR/ALUNO deixaram de ser role base única e viraram **flags** (`eh_admin`/`eh_professor`/`eh_aluno`, migration **V19**; a coluna `role` saiu), somando-se a `eh_tesoureiro`/`eh_lider`. Um **único usuário acumula papéis** (professor + aluno no mesmo login). O `TokenService` emite um grupo JWT por flag → `@RolesAllowed`/`EscopoService` inalterados. Front: tela de Usuários por **checkboxes** e um **alternador "Atuando como"** (Gestão ↔ Aluno) no topo, que troca só o foco do menu. Contas duplicadas: migração 1:1 + **consolidação manual** ([`docs/unificacao-papeis.md`](docs/unificacao-papeis.md)). Validado: 38 testes (`PapeisFlagsTest`), `ng build`, e por API (usuário professor+aluno acessa as duas superfícies). Branch `feature/unificar-papeis-usuario`.
- 📝 **Falta justificada (30%) + status automático (2026-07-28)** — migration **V24** (`justificada`/`justificativa_motivo`/`justificada_em` em `presenca`). (1) Somente o **professor** justifica a falta, ao **salvar a chamada** (coluna *Falta justificada* por aluno ausente — `ChamadaService`/`SalvarChamadaRequest`); uma falta justificada passa a valer **0,3** (30% de uma presença) no ranking *menos faltou* e na *classificação geral* (`DesafiosService`). O aluno apenas **visualiza** a justificativa em `/minha-frequencia` (não há mais autosserviço; os endpoints `/api/me/frequencia/{aulaId}/justificar` foram removidos). (2) Ao salvar a chamada, aluno com **>4 faltas seguidas sem justificativa** (5ª) é **inativado** automaticamente (presença ou falta justificada zera a sequência) — `ChamadaService`, com alerta no toast + Auditoria. (3) Ao cadastrar visitante, quem foi às **3 aulas seguidas** mais recentes da turma (identidade = nome + telefone/e-mail) **vira aluno** com login automático — `VisitanteService` + `AcessoAlunoService`. Validado: `FaltaJustificadaEInativacaoTest` (5 casos), **51** testes verdes, `ng build`. Os 2 eventos automáticos também enviam e-mail (aluno inativado; visitante promovido) via `NotificacaoService`, respeitando o toggle `ebd.notificacoes.enabled`.
- 🏫 **Ranking por turma (desafio entre classes) (2026-07-29)** — além do ranking individual, um
  ranking das **turmas** entre si, pontuadas pela **média de pontos por aluno** (total da turma ÷ nº de
  alunos ativos), para turmas de tamanhos diferentes competirem de forma justa. Reusa as mesmas fórmulas
  (0,3 por falta justificada, 2 pts por visitante, notas ×5, exclusão do aluno-professor). **Sem migration.**
  Backend: `DesafiosService.gerarPorTurma`/`resumoTurmasDoAluno` (agregação por `classe.id`), DTO
  `RankingTurmasResponse`, endpoints `GET /api/desafios/rankings-turmas` (ADMIN/PROFESSOR, respeita escopo)
  e `GET /api/me/ranking-turmas` (ALUNO, destaca a própria turma). Front: aba **Individual/Por turma** em
  `/desafios` (a de turma ignora o seletor global e compara todas do escopo) e seção "Ranking das turmas"
  em `/meu-ranking`. Validado: `mvn package` (novo `rankingPorTurmaUsaMediaPorAluno` em `DesafiosServiceTest`)
  e `ng build`. Branch `feature/ranking-por-turma`.
- 🔁 **Aula complementar + empurrão da agenda (2026-07-29)** — para quando uma lição não termina no domingo e precisa continuar no domingo seguinte (que já está ocupado na agenda pré-montada). Botão **"Desdobrar"** por linha em /aulas: cria a continuação em `origem + 7 dias` (tema herdado com sufixo "(continuação)", mesmo professor — ambos editáveis) e **empurra +7 dias toda a agenda seguinte da turma**. O empurrão desloca as aulas afetadas em **ordem decrescente de data com flush por iteração** — a mais recente vai ao slot vazio e cada anterior ocupa o recém-liberado, sem nunca violar `uq_aula_classe_data` (não-deferrable). **Sem migration.** Backend: `AulaService.complementar` + `AulaRepository.listarPorClasseDesde`, DTOs `AulaComplementarRequest/Response`, endpoint `POST /api/aulas/{id}/complementar` (ADMIN/PROFESSOR, respeita escopo). Front: modal com prévia ("N aulas serão movidas") na tela Aulas. Validado: `mvn package` (novo `AulaComplementarServiceTest`, 2 casos: empurra 3 e origem-última não move nada) e `ng build`. Branch `feature/aula-complementar-empurra-agenda`.
- ⏳ **Deploy na OCI em andamento** — aguardando capacidade A1 (retry rodando).

## 10. Como pedir evoluções (dica para o Danilo)

Ao começar uma nova feature, vale pedir algo como: *"leia o CLAUDE.md e o docs/ARQUITETURA.md
e implemente X"*. Fluxos comuns:
- Nova entidade/campo → criar migration `V{n}__...`, entidade, repository, service, DTO,
  resource, e telas/serviço no front.
- Antes de commitar mudança relevante, rodar as validações da seção 5.
- Backlog e ideias já mapeadas: [`docs/ROADMAP.md`](docs/ROADMAP.md).
