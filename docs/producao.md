# Produção — acesso e operação 🌐

Ambiente de produção na Oracle Cloud (OCI). **Topologia de 2 VMs** — visão completa em
[`topologia.md`](topologia.md).

## Acesso

| O quê | Valor |
|---|---|
| **URL do site** | **https://ebd-ices.duckdns.org** |
| **Swagger da API** | https://ebd-ices.duckdns.org/q/swagger-ui |
| **Health** | https://ebd-ices.duckdns.org/q/health |
| **Login** | usuários `admin` e `professor` — senhas no seu **gerenciador de senhas** (ver [`senhas-e-secrets.md`](senhas-e-secrets.md)) |

> **HTTPS ativo** via Caddy + Let's Encrypt (DuckDNS `ebd-ices.duckdns.org`), com renovação automática e redirect HTTP→HTTPS.
>
> AVISO: o IP público das VMs é efêmero — se você parar e iniciar a instância (reboot não conta), o IP pode mudar; atualize o IP no DuckDNS (app) e o `EBD_DB_HOST`/Security List se mudar o **privado** da db.

## As VMs

| | ebd-server (app) | ebd-db (banco) |
|---|---|---|
| Shape | E2.1.Micro (1 OCPU / 1 GB, Always Free) | idem |
| IP público / privado | 163.176.181.38 / 10.0.1.45 | 136.248.80.0 / 10.0.1.54 |
| SSH | `ssh ubuntu@163.176.181.38` | `ssh ubuntu@136.248.80.0` |
| Roda | `caddy` + `frontend` + `backend` (`~/ebd-samambaia`, `docker-compose.app.yml`) | Postgres (`~/ebd-db`, `docker-compose.db.yml`) |

Chave SSH: `~/.ssh/id_ed25519`. Detalhes de rede/segurança em [`topologia.md`](topologia.md).

## Como o deploy acontece

Merge de PR na **`main`** → **CI** → **CD**: builda as imagens no runner, publica no **GHCR privado**
e a `ebd-server` faz `docker compose pull && up` (**sem build na VM**, ~2 min). Ver [`CICD.md`](CICD.md).
Deploy manual: `gh workflow run "CD · Deploy OCI"`.

## Operação (via SSH)

```bash
# --- App (ebd-server) ---
ssh ubuntu@163.176.181.38
cd ~/ebd-samambaia
docker compose -f docker-compose.app.yml ps                  # status
docker compose -f docker-compose.app.yml logs -f backend     # logs (backend/frontend/caddy)
docker compose -f docker-compose.app.yml restart backend     # reiniciar um serviço
docker compose -f docker-compose.app.yml --env-file .env up -d   # aplicar mudança de .env
docker compose -f docker-compose.app.yml --env-file .env pull && \
  docker compose -f docker-compose.app.yml --env-file .env up -d  # puxar imagem nova (rollback: EBD_IMAGE_TAG=<sha> no .env)

# --- Banco (ebd-db) ---
ssh ubuntu@136.248.80.0
docker exec -it ebd-postgres psql -U ebd -d ebd              # console SQL (ex.: SELECT count(*) FROM aluno;)
```

## Backups

Rodam **na ebd-db** (o deploy não faz mais `pg_dump`). Cron diário + offsite no OCI Object Storage.
Ver [`topologia.md`](topologia.md#backups). Instalação:
```bash
./scripts/setup-backup-ebd-db.sh     # cron local na ebd-db
./scripts/setup-offsite-oci.sh       # offsite no Object Storage (PAR)
```
Restaurar (na ebd-db):
```bash
gzip -dc ~/backups/ebd-AAAAMMDD-HHMM.sql.gz \
  | docker exec -i ebd-postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

## Trocar senhas / secrets

Ver [`senhas-e-secrets.md`](senhas-e-secrets.md). Em resumo: atualiza o `.env` na `ebd-server`
(app/SMTP) ou na `ebd-db` (banco) + o secret `OCI_ENV_FILE`, e aplica com o `docker compose ... up -d`
do host correspondente.

## Diagnóstico rápido

```bash
curl -sI https://ebd-ices.duckdns.org            # frontend 200
curl -s  https://ebd-ices.duckdns.org/q/health   # {"status":"UP",...}
# 5432 NÃO pode responder da internet (rode de fora da VCN):
nc -vz 136.248.80.0 5432                          # deve falhar
```

## Custo

Tudo em **Always Free**: 2 VMs E2.1.Micro + 10 GB de Object Storage. Custo esperado **US$ 0**.
Para blindar, crie um Budget/alerta em Billing → Cost Management.
