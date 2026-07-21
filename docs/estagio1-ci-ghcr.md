# Estágio 1 — build no CI + imagens no GHCR (fim do build lento na VM)

As imagens passam a ser **buildadas no GitHub Actions** e publicadas no **GHCR (privado)**; a VM de
app só faz `docker compose pull && up` (sem build). Deploy cai de ~20 min para ~2 min e some a
disputa build×runtime. As **chaves JWT** viram volume (`/keys`) — a imagem fica sem segredo e os
tokens sobrevivem aos deploys.

## O que mudou no repo (branch `feature/split-db-2vm`)
- `docker-compose.app.yml`: backend/frontend usam `image: ghcr.io/daniloav/ebd-*:${EBD_IMAGE_TAG:-latest}`;
  backend monta `./keys:/keys:ro` e lê as chaves de lá (`EBD_JWT_*_LOCATION`).
- `backend/src/main/resources/application.properties`: localização das chaves JWT configurável
  (padrão = classpath, para dev). **Validado**: dev sobe e faz login lendo as chaves de arquivo.
- `.github/workflows/cd.yml`: job **build** (push das imagens no GHCR) + job **deploy** (pull na VM,
  grava chaves no `/keys`, `docker login ghcr`, `up` sem build). O `.env` ganha `EBD_DB_HOST` garantido.
- O Dockerfile do backend **não muda**: segue gerando chaves como fallback (que a produção ignora,
  pois aponta para `/keys`).

## Pré-requisitos (você faz uma vez)

### 1. PAT para a VM baixar imagens privadas
Crie um **Personal Access Token (classic)** com escopo **`read:packages`**:
GitHub → Settings → Developer settings → Tokens (classic) → Generate. Cadastre 2 secrets no repo:
```bash
gh secret set EBD_GHCR_USER --body "daniloav"
gh secret set EBD_GHCR_PAT   --body "<o PAT read:packages>"
```
> O push das imagens (no Actions) usa o `GITHUB_TOKEN` embutido — não precisa de PAT. O PAT é só para
> a **VM puxar** as imagens privadas.

### 2. Garantir o `.env` de produção no secret
Idealmente adicione ao secret **`OCI_ENV_FILE`** as linhas que faltam (o CD também garante o
`EBD_DB_HOST`, mas o ideal é o secret ser a fonte completa):
```env
EBD_DB_HOST=10.0.1.54
EBD_SMTP_HOST=smtp.gmail.com
EBD_SMTP_USER=<conta gmail>
EBD_SMTP_PASS=<senha de app>
```

### 3. Visibilidade das imagens
Na 1ª publicação, os pacotes `ebd-backend`/`ebd-frontend` nascem **privados** (o que você quer).
Confirme em github.com/users/daniloav/packages depois do 1º build.

## Ativar (merge → deploy)
Como o Docker não builda no seu Mac, a única forma de gerar as imagens é pelo Actions. A ativação é
um deploy real — faça num **horário tranquilo** e acompanhe:
1. Merge de `feature/split-db-2vm` (+ as outras branches) na `main`.
2. O CI roda; ao terminar, o **CD**: builda e publica as imagens, a VM faz `login`+`pull`+`up`.
3. Acompanhe as Actions e depois: `curl -fsS https://ebd-ices.duckdns.org/q/health`.

## Rollback (se o deploy com imagem falhar)
A app continua servível pela stack atual até o `up` novo. Se precisar voltar:
```bash
ssh ubuntu@163.176.181.38 'cd ~/ebd-samambaia && git -C . describe 2>/dev/null; \
  # subir uma imagem anterior por SHA:
  echo "EBD_IMAGE_TAG=<sha-anterior>" >> .env && \
  docker compose -f docker-compose.app.yml --env-file .env up -d'
```
Ou reverter o merge na `main` (o deploy seguinte reconstrói a partir do estado anterior). O volume
`ebd_pgdata` da VM-db **não é tocado** pelo deploy da app.

## Pendência: backup do banco agora é na VM-db
O CD não faz mais `pg_dump` (a app não toca o banco). Agende um backup **na ebd-db** (cron):
```bash
ssh ubuntu@136.248.80.0 'crontab -l 2>/dev/null; echo "0 4 * * * docker exec ebd-postgres pg_dump -U ebd ebd | gzip > ~/backup-\$(date +\%F).sql.gz" | crontab -'
```

## Não valida daqui
Este estágio **não foi testado ponta-a-ponta** (sem push ao GHCR nem acesso à VM a partir do
ambiente de desenvolvimento). Foi validado: build local (`mvn package`, `ng build`), carregamento
das chaves JWT por arquivo em dev, e o YAML dos workflows. O 1º deploy real é a validação final.
