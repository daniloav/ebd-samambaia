# Estágio 2 — Postgres numa VM dedicada (topologia de 2 VMs)

Separa o banco numa 2ª VM Always Free para dar **1 GB inteiro** ao Postgres e ao backend
(hoje disputam 1 GB na mesma VM). Faz parte da avaliação de performance/escala.

## Topologia

| VM | Papel | IP público | IP privado |
|---|---|---|---|
| `ebd-server` | app: caddy + frontend + backend | 163.176.181.38 | **10.0.1.45** |
| `ebd-db` | banco: Postgres | 136.248.80.0 | **10.0.1.54** |

Ambas na mesma subnet → o backend fala com o Postgres pelo **IP privado** (10.0.1.54).
A porta 5432 **nunca** fica pública: o container é vinculado ao IP privado e a Security List
só libera 5432 vindo do IP privado da app.

## Arquivos

- `docker-compose.db.yml` — Postgres só, para a `ebd-db` (tunado: shared_buffers 256MB, 50 conexões).
- `docker-compose.app.yml` — app sem Postgres, backend aponta para `EBD_DB_HOST`; `mem_limit` do
  backend sobe para 700m (sobra RAM sem o banco local).
- `docker-compose.yml` (all-in-one) segue existindo para dev/local e como fallback.

---

## Cutover (executar na ordem; downtime de poucos minutos)

### 1. Preparar a VM-db (`ssh ubuntu@136.248.80.0`)
Docker já instalado pelo `oci-bootstrap.sh`. Crie o `~/ebd-db/.env`:
```env
POSTGRES_DB=ebd
POSTGRES_USER=ebd
POSTGRES_PASSWORD=<a MESMA senha do banco de produção>
EBD_DB_BIND_IP=10.0.1.54
```
Copie o `docker-compose.db.yml` para `~/ebd-db/` e suba o Postgres:
```bash
cd ~/ebd-db && sudo docker compose -f docker-compose.db.yml --env-file .env up -d
sudo docker compose -f docker-compose.db.yml logs --tail 20 db   # deve ficar "ready to accept connections"
```

### 2. Liberar a porta 5432 só para a app (Security List)
Adicione **1 regra de ingress** na Security List da subnet: origem **10.0.1.45/32**, TCP destino **5432**.
Pelo console (Networking → VCN → Subnet → Security List → Add Ingress), ou por CLI (posso gerar o
comando exato com o OCID da security list quando você chegar aqui).
> Como o container já está vinculado a 10.0.1.54, ele não responde do IP público de qualquer forma —
> a regra é a segunda camada.

### 3. Migrar os dados (na VM-app, `ssh ubuntu@163.176.181.38`)
```bash
cd ~/ebd-samambaia
source .env    # para pegar POSTGRES_PASSWORD
# despeja o banco atual e restaura no Postgres da VM-db (via IP privado)
sudo docker exec ebd-postgres pg_dump -U ebd -d ebd \
  | sudo docker run -i --rm -e PGPASSWORD="$POSTGRES_PASSWORD" postgres:16-alpine \
      psql -h 10.0.1.54 -U ebd -d ebd
```
O dump traz schema + dados + `flyway_schema_history`, então o Flyway do backend vê tudo aplicado
e não recria nada.

### 4. Virar a app para o banco remoto (na VM-app)
No `~/ebd-samambaia/.env`, adicione: `EBD_DB_HOST=10.0.1.54`.
Suba com o compose de app (sem o Postgres local) e remova o container antigo do banco:
```bash
cd ~/ebd-samambaia
sudo docker compose -f docker-compose.app.yml --env-file .env up -d --build
sudo docker rm -f ebd-postgres      # remove o Postgres local (dados já migraram)
sudo docker compose -f docker-compose.app.yml exec -T caddy caddy reload --config /etc/caddy/Caddyfile || true
```

### 5. Verificar
```bash
curl -sf https://ebd-ices.duckdns.org/q/health && echo OK
sudo docker logs --tail 40 ebd-backend | grep -iE "flyway|datasource|listening"   # conectou no 10.0.1.54
# 5432 NÃO pode responder da internet:
nc -vz 136.248.80.0 5432   # deve dar timeout/refused (rode de fora da VCN)
```
Login e uma chamada de teste pela UI devem funcionar normalmente.

### Backup passa a rodar na VM-db
O `scripts/backup-db.sh` (que hoje roda `docker exec ebd-postgres pg_dump` na app) deve rodar na
**ebd-db**. Ajustar o CD/cron quando fizermos o Estágio 1 (deploy em 2 hosts).

## Rollback
Se algo falhar no passo 4/5: volte o `docker-compose.yml` all-in-one
(`sudo docker compose up -d` sem `EBD_DB_HOST`) — o Postgres local ainda tem os dados até você
remover o container/volume. Só remova `ebd_pgdata` da app depois de validar o banco novo.

## Pendências ligadas
- **Estágio 1** (build no CI + GHCR): o `cd.yml` passará a fazer deploy nos **2 hosts** e a app usará
  `docker-compose.app.yml` por padrão.
- Atualizar `docs/producao.md` com a 2ª VM depois do cutover validado.
