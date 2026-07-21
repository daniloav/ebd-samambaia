# CI/CD — GitHub Actions

Três workflows em `.github/workflows/`.

## Fluxo de branches (develop → main)

```
  feature/dev ──▶ push develop ──▶ CI + CodeQL (validam)  [NÃO faz deploy]
                       │
                       ▼  (testar local com ./scripts/dev-up.sh)
                    Pull Request ──▶ CI roda no PR (precisa passar p/ poder mergear)
                       │
                       ▼  merge na main ──▶ CI + CD ──▶ build imagens (GHCR) + pull na VM 🚀
```

- **`develop`** — branch de trabalho. Todo dia-a-dia vai aqui.
- **`main`** — protegida; só recebe código via **Pull Request** com o CI verde. Merge na main
  **dispara o deploy** (CD) para a VM.

### Onde cada workflow roda

| Workflow | develop | main | Pull Request |
|---|:--:|:--:|:--:|
| **CI** (build + Semgrep/Trivy/gitleaks) | ✅ | ✅ | ✅ |
| **CodeQL** | ✅ | ✅ | ✅ (pulado em repo privado) |
| **CD** (deploy OCI) | ❌ | ✅ | ❌ |

O CD só dispara via `workflow_run` **filtrado para a `main`** — nunca para a develop.

### Proteção da `main`

> ⚠️ **Proteção de branch em repo PRIVADO exige GitHub Pro** (ou tornar o repo público).
> No plano free-privado a trava técnica não pode ser ativada. Enquanto isso, o fluxo
> `develop → PR → main` é seguido por **convenção** (o CI ainda valida os PRs).

Regras desejadas (aplicar quando houver Pro / repo público — via `gh api PUT .../branches/main/protection`):
- **Exige Pull Request** antes de mergear (0 aprovações — ok para 1 dev só).
- **Exige os checks do CI passando**: Backend, Frontend, SAST · Semgrep, Trivy, gitleaks.
- **Sem push direto**, sem force-push, sem deleção da branch.
- Admin **não** bloqueado (`enforce_admins=false`).

### Dia a dia

```bash
git checkout develop
git pull
# ... trabalha, commita ...
git push                              # CI roda na develop (sem deploy)
./scripts/dev-up.sh                   # testa local
gh pr create --base main --head develop --fill   # abre o PR
# CI roda no PR; quando verde, faça o merge (pela UI ou:)
gh pr merge --squash --delete-branch=false        # merge → CD faz o deploy
```



## CI (`ci.yml`) — a cada push/PR na `main`

| Job | O que faz | Bloqueia? |
|---|---|---|
| **Backend · build & testes** | `mvn verify` (Quarkus) com JDK 17; gera chaves JWT antes | ✅ sim |
| **Frontend · build** | `npm ci` + `ng build` (Node 20) | ✅ sim |
| **SAST · Semgrep** | Análise estática de código (packs `security-audit`, `secrets`, `java`, `javascript`, `typescript`, `dockerfile`) | ✅ falha se achar problema |
| **Dependências & IaC · Trivy** | Vulnerabilidades de dependências, má-configuração e segredos | ⚠️ relatório HIGH/CRITICAL + **falha em CRITICAL** |
| **Segredos · gitleaks** | Procura segredos no código atual | ✅ falha se achar segredo |

Relatórios do Semgrep e do Trivy ficam como **artefatos** do run (aba *Summary → Artifacts*).

## CodeQL (`codeql.yml`) — SAST "security-and-quality"

- Usa o pacote **`security-and-quality`** do CodeQL (segurança **+** qualidade), para Java e JS/TS.
- ⚠️ **Importante**: CodeQL / Code Scanning é **gratuito só em repositórios públicos**
  (ou privados com **GitHub Advanced Security**, que é pago). Como o repo é **privado**, este
  workflow é **pulado automaticamente** (`if: repository.private == false`) para não falhar a esteira.
- **Ao tornar o repo público, ele passa a rodar sozinho** e popula a aba **Security → Code scanning**.
- Enquanto isso, a cobertura de SAST fica com o **Semgrep** (que funciona em repo privado, de graça).

## CD (`cd.yml`) — deploy no OCI (2 VMs + GHCR)

Dispara **após o CI passar na `main`** (ou `workflow_dispatch`). Topologia atual em
[`topologia.md`](topologia.md). São **dois jobs**:

1. **build** — builda `ebd-backend` e `ebd-frontend` no runner e publica no **GHCR privado**
   (`ghcr.io/daniloav/ebd-*`, tags `latest` + `<sha>`), usando o `GITHUB_TOKEN` embutido.
   Antes, faz o *bump* de versão (SemVer) e a tag `vX.Y.Z`.
2. **deploy** — na VM `ebd-server`: envia o `.env`, grava as **chaves JWT** em `~/ebd-samambaia/keys`
   (a partir dos secrets), sincroniza os arquivos de config (compose/Caddyfile/scripts, **sem build**),
   faz `docker login ghcr` e `docker compose -f docker-compose.app.yml pull && up -d` → **healthcheck**.

O deploy **não builda na VM** (deploy ~2 min) e **não toca o banco** (o Postgres roda na `ebd-db`).
Rollback: `EBD_IMAGE_TAG=<sha>` no `.env` da VM + `up -d`, ou reverter o merge. Detalhes e ativação
em [`estagio1-ci-ghcr.md`](estagio1-ci-ghcr.md).

### Secrets do repositório (Settings → Secrets → Actions)

| Secret | Para quê | Sensível? |
|---|---|---|
| `OCI_SSH_HOST` / `OCI_SSH_USER` | IP público e usuário SSH da `ebd-server` | não |
| `OCI_SSH_KEY` | chave **privada** de deploy | **sim** |
| `OCI_ENV_FILE` | `.env` de produção (senhas do banco/app, SMTP, `EBD_DB_HOST`) | **sim** |
| `EBD_JWT_PRIVATE_KEY` / `EBD_JWT_PUBLIC_KEY` | chaves JWT persistentes (montadas em `/keys`) | privada: **sim** |
| `EBD_GHCR_USER` | usuário do GHCR (`daniloav`) — para a VM baixar imagem privada | não |
| `EBD_GHCR_PAT` | PAT **classic** com `read:packages` (login na VM) | **sim** |

> O **push** das imagens usa o `GITHUB_TOKEN` (não precisa de PAT). O `EBD_GHCR_PAT` é só para a **VM
> puxar** imagens privadas. Enquanto os secrets `OCI_*` forem placeholder, o deploy roda em **modo mock**.

## Provisionamento das VMs

Feito uma vez por VM (ver [`topologia.md`](topologia.md) e [`deploy-oracle.md`](deploy-oracle.md)):
lançar a E2.1.Micro (script de retry), rodar `oci-bootstrap.sh` (Docker + portas), e — para a `ebd-db` —
o split do banco via [`estagio2-db-separado.md`](estagio2-db-separado.md) / `scripts/estagio2-cutover.sh`.
