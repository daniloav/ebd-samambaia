# Produção — acesso e operação 🌐

Dados e comandos do ambiente de produção (VM na Oracle Cloud).

## Acesso

| O quê | Valor |
|---|---|
| **URL do site** | **https://ebd-ices.duckdns.org** (IP direto: 163.176.181.38) |
| **Swagger da API** | https://ebd-ices.duckdns.org/q/swagger-ui |
| **Health** | https://ebd-ices.duckdns.org/q/health |
| **Login** | usuários `admin` e `professor` — senhas no seu **gerenciador de senhas** (ver [`senhas-e-secrets.md`](senhas-e-secrets.md)) |

> **HTTPS ativo** via Caddy + Let's Encrypt (dominio DuckDNS ebd-ices.duckdns.org), com renovacao automatica do certificado e redirect HTTP->HTTPS.
>
> AVISO: o IP publico da VM e efemero — se voce parar e iniciar a instancia (reboot nao conta), o IP pode mudar; entao atualize o IP no DuckDNS.

## A VM

| Item | Valor |
|---|---|
| Provedor / região | Oracle Cloud (OCI) · `sa-saopaulo-1` |
| Nome / shape | `ebd-server` · `VM.Standard.E2.1.Micro` (x86, 1 OCPU / 1 GB, Always Free) |
| SO | Ubuntu 22.04 · **3 GB de swap** |
| Acesso SSH | `ssh ubuntu@163.176.181.38` (sua chave `~/.ssh/id_ed25519`) |
| App na VM | `~/ebd-samambaia` (Docker Compose: `ebd-postgres`, `ebd-backend`, `ebd-frontend`) |

## Como o deploy acontece

Merge de um PR na **`main`** → o workflow **CD** faz o deploy automático (rsync + build na VM).
Ver [`CICD.md`](CICD.md). Deploy manual: `gh workflow run "CD · Deploy OCI"`.

## Operação (rodar na VM via SSH)

```bash
ssh ubuntu@163.176.181.38
cd ~/ebd-samambaia

sudo docker compose ps                 # status dos containers
sudo docker compose logs -f backend    # logs do backend (ou frontend/db)
sudo docker compose restart backend    # reiniciar um serviço
sudo docker compose up -d              # subir (após mudar .env)
sudo docker compose up -d --build      # rebuild + subir (lento em 1 GB)
```

### Backup e restore do banco
```bash
# backup
sudo docker exec ebd-postgres pg_dump -U ebd ebd > ~/backup_$(date +%F).sql
# restore
cat backup.sql | sudo docker exec -i ebd-postgres psql -U ebd -d ebd
```

### Consultar o banco direto
```bash
sudo docker exec -it ebd-postgres psql -U ebd -d ebd
# ex.: SELECT count(*) FROM aluno;
```

## Trocar senhas / secrets

Ver [`senhas-e-secrets.md`](senhas-e-secrets.md) (seção "Rotacionar"). Em resumo: atualiza o
`.env` na VM + o secret `OCI_ENV_FILE`, e aplica com `docker compose up -d`
(ou `down -v && up -d` se precisar reinicializar o banco).

## Verificar / diagnosticar rápido

```bash
curl -I http://163.176.181.38            # frontend deve dar 200
curl -s http://163.176.181.38/q/health   # {"status":"UP",...}
```

## Custo

Tudo em recursos **Always Free** (a instância tem a tag `free-tier-retained: true`).
Custo esperado: **US$ 0**. Para blindar, crie um Budget/alerta em Billing → Cost Management.
