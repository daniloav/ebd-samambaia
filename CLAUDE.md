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
| Infra | Docker · Docker Compose · nginx (serve o Angular + proxy `/api`) |
| Cloud | Oracle Cloud (OCI) — VM Always Free (A1.Flex ARM), região `sa-saopaulo-1` |

Ambiente local do Danilo: Java 19 (roda o target 17), Maven 3.9, **Node 18.13** (por isso
Angular **17**, não 18+), Docker. Shell: **zsh** (atenção: scripts usam shebang bash).

## 3. Mapa do repositório

```
claude-trabalho/
├── CLAUDE.md                      ← este arquivo
├── README.md                      ← visão geral + como rodar
├── docker-compose.yml             ← stack completo (prod/VM): db + backend + frontend
├── docker-compose.dev.yml         ← só Postgres (dev)
├── .env.example                   ← credenciais do compose de produção
├── backend/                       ← API Quarkus (pacote br.com.ice.ebd)
│   ├── pom.xml
│   ├── Dockerfile                 ← multi-stage; gera chaves JWT se ausentes
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
├── .github/workflows/             ← CI (build + SAST/segurança) e CD (deploy OCI)
├── scripts/                       ← automação de deploy OCI (ver seção 8)
└── docs/
    ├── ARQUITETURA.md             ← modelo de dados, camadas, decisões (LER para mudanças estruturais)
    ├── migrations.md              ← changelog das migrations (Flyway) + como recriar o banco
    ├── API.md                     ← referência de endpoints com exemplos
    ├── ROADMAP.md                 ← backlog e limitações conhecidas
    ├── rodar-local.md             ← como rodar localmente (dev, passo a passo)
    ├── CICD.md                    ← esteira GitHub Actions (CI segurança + CD OCI) + fluxo de branches
    ├── producao.md                ← produção: URL/IP, SSH, operação
    ├── senhas-e-secrets.md        ← onde ficam senhas/secrets e como consultar
    ├── pos-vm.md                  ← runbook: deploy quando a VM subir (bootstrap + secrets)
    └── deploy-oracle.md           ← passo a passo de deploy na VM
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

## 8. Deploy na OCI (estado atual)

- **Região**: `sa-saopaulo-1` (uma única AD). **Rede já criada** via CLI: VCN pública +
  Internet Gateway + rota `0.0.0.0/0` + subnet pública + Security List liberando portas 22 e 80.
- **VM alvo (atual)**: `ebd-server`, shape **`VM.Standard.E2.1.Micro`** (x86, **1 OCPU / 1 GB**, Always Free),
  Ubuntu 22.04. Mudança do A1.Flex (6 GB) por "Out of capacity" persistente do A1 em São Paulo.
  Como é 1 GB: `oci-bootstrap.sh` cria **3 GB de swap**, `docker-compose` tem `mem_limit` por serviço
  e o backend usa `-XX:MaxRAMPercentage`. Voltar ao A1 (6 GB) via **Pay As You Go** está no ROADMAP.
- O script `scripts/oci-a1-retry.sh` é **agnóstico ao shape** (lê `SHAPE` do `.oci-launch.env`)
  e tenta com retry no "Out of capacity".
- Scripts (em `scripts/`):
  - `oci-descobrir.sh` — lista OCIDs (compartment, AD, imagem, subnet).
  - `oci-a1-retry.sh` — cria a VM com retry no "Out of capacity" (lê `scripts/.oci-launch.env`).
  - `gen-jwt-keys.sh` — gera as chaves JWT do backend.
  - `oci-bootstrap.sh` — roda NA VM: instala Docker/Compose e libera portas 80/443.
  - `.oci-launch.env` (local, ignorado) — OCIDs reais; modelo em `.oci-launch.env.example`.
- **CI/CD** (GitHub Actions, ver [`docs/CICD.md`](docs/CICD.md)):
  - `ci.yml` — build backend/frontend + segurança (Semgrep SAST, Trivy deps/IaC, gitleaks segredos).
  - `codeql.yml` — CodeQL `security-and-quality` (só roda em repo público/GHAS; pulado no privado).
  - `cd.yml` — deploy no OCI via rsync+SSH; **modo mock** até cadastrar os secrets `OCI_*`.
- **Uso persistente do retry** (sobrevive a fechar o terminal):
  ```bash
  nohup ./scripts/oci-a1-retry.sh > ~/ebd-launch.log 2>&1 &
  tail -f ~/ebd-launch.log
  ```
- Guia completo: [`docs/deploy-oracle.md`](docs/deploy-oracle.md).

## 9. Estado do projeto (atualizar aqui a cada avanço)

- ✅ MVP completo (backend + frontend + infra + scripts + docs).
- ✅ Backend passa no `mvn package`; frontend passa no `ng build`.
- ✅ Código no GitHub: `git@github.com:daniloav/ebd-samambaia.git` (repo **privado**), branch `main`.
- ✅ Segredos removidos do versionamento. CI/CD verde (CD em mock até secrets reais).
- ✅ **Runtime VALIDADO (2026-07-19)** — smoke test ponta-a-ponta OK: Flyway migrou, seed criou
  usuários/alunos, login JWT, chamada (upsert), provas+notas, rankings e relatório responderam
  contra Postgres real; login pela UI navega ao painel com dados reais.
- 🐳 **Docker local não funciona neste Mac** (daemon trava — `docker ps` pendura). Para dev local,
  use **Postgres nativo** em vez do `docker-compose.dev.yml`:
  `brew services start postgresql@16` + role/db `ebd` (senha `ebd`) na porta 5432.
  Na VM (deploy) o Docker é limpo e funciona normalmente.
- 📧 **Alertas por e-mail** (roadmap item 2): ao salvar a chamada, envia e-mail aos alunos com opt-in
  (`recebe_notificacoes` + `email`, migration V4). Toggle `ebd.notificacoes.enabled` (off em prod até
  configurar SMTP). Validado em dev com mailer *mock*. Guia: [`docs/notificacoes-email.md`](docs/notificacoes-email.md).
- ⏳ **Deploy na OCI em andamento** — aguardando capacidade A1 (retry rodando).

## 10. Como pedir evoluções (dica para o Danilo)

Ao começar uma nova feature, vale pedir algo como: *"leia o CLAUDE.md e o docs/ARQUITETURA.md
e implemente X"*. Fluxos comuns:
- Nova entidade/campo → criar migration `V{n}__...`, entidade, repository, service, DTO,
  resource, e telas/serviço no front.
- Antes de commitar mudança relevante, rodar as validações da seção 5.
- Backlog e ideias já mapeadas: [`docs/ROADMAP.md`](docs/ROADMAP.md).
