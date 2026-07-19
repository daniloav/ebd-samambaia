# CI/CD — GitHub Actions

Três workflows em `.github/workflows/`.

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

## CD (`cd.yml`) — deploy no OCI

Dispara **manualmente** (`workflow_dispatch`) ou **após o CI passar** na `main`.

### Modo MOCK (atual)
Enquanto os secrets `OCI_*` não existirem, o job roda em **modo mock**: não faz deploy,
só registra os passos que executaria. Assim a esteira fica verde até a VM ser provisionada.

### Ativando o deploy real
Quando a VM estiver no ar, cadastre em **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Valor |
|---|---|
| `OCI_SSH_HOST` | IP público da VM (ex.: `140.238.x.x`) |
| `OCI_SSH_USER` | `ubuntu` |
| `OCI_SSH_KEY` | conteúdo da **chave privada** de deploy (ver abaixo) |
| `OCI_ENV_FILE` | *(opcional)* conteúdo do `.env` de produção (senhas fortes) |

O deploy então: configura SSH → (envia `.env`) → **rsync** do código para `~/ebd-samambaia`
na VM → `docker compose up -d --build` → **healthcheck** em `http://IP/q/health`.

### Chave de deploy (recomendado: chave dedicada)
Não reutilize sua chave pessoal. Na sua máquina:
```bash
ssh-keygen -t ed25519 -f ~/.ssh/ebd_deploy -C "deploy-actions" -N ""
# 1) cole ~/.ssh/ebd_deploy.pub em ~/.ssh/authorized_keys da VM (ou no cloud-init)
# 2) cole o conteúdo de ~/.ssh/ebd_deploy (PRIVADA) no secret OCI_SSH_KEY
```

## Preparando a VM

Antes do primeiro deploy, rode o [`scripts/oci-bootstrap.sh`](../scripts/oci-bootstrap.sh) **na VM**
(instala Docker + Compose e libera as portas 80/443). Detalhes em [`deploy-oracle.md`](deploy-oracle.md).

## Ordem recomendada

1. VM provisionada (script de retry A1) → anote o IP público.
2. Rodar `oci-bootstrap.sh` na VM.
3. Gerar a chave de deploy e adicionar `.pub` na VM.
4. Cadastrar os secrets `OCI_*` no GitHub.
5. Disparar o **CD** (manual ou push na `main`) → site no ar.
