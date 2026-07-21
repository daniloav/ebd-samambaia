#!/usr/bin/env bash
#
# Estágio 2 — separa o Postgres na 2ª VM (ebd-db) e vira a app para o banco remoto.
# RODA NO SEU MAC (orquestra as 2 VMs por SSH + a Security List por `oci`).
#
# Uso:
#   ./scripts/estagio2-cutover.sh            # faz o PREP (não-destrutivo) e pergunta se vira a app
#   ./scripts/estagio2-cutover.sh prep       # só o preparo (Postgres na db + firewall + migração)
#   ./scripts/estagio2-cutover.sh cutover     # só a virada da app (assume prep já feito)
#
# Pré-requisitos: `oci` configurado, SSH nas 2 VMs (chave ~/.ssh/id_ed25519), Docker já instalado
# nas 2 VMs. NÃO guarda senhas: lê a senha real do container Postgres em execução.
#
set -euo pipefail

# ---------------- Config (valores reais do ambiente) ----------------
APP_PUB="163.176.181.38"      # ebd-server (app) — IP público
APP_PRIV="10.0.1.45"          # ebd-server (app) — IP privado
DB_PUB="136.248.80.0"         # ebd-db (banco) — IP público
DB_PRIV="10.0.1.54"           # ebd-db (banco) — IP privado
SSH_USER="ubuntu"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_ed25519}"
APP_DIR="ebd-samambaia"       # diretório do app na VM-app
DB_DIR="ebd-db"               # diretório do banco na VM-db
SECLIST_ID="ocid1.securitylist.oc1.sa-saopaulo-1.aaaaaaaapao5mxuw3zeotvoc7oq4wmpd5whneyj77l5y54o777dezjizxtia"

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SSH_OPTS=(-o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 -i "$SSH_KEY")
ssh_app() { ssh "${SSH_OPTS[@]}" "$SSH_USER@$APP_PUB" "$@"; }
ssh_db()  { ssh "${SSH_OPTS[@]}" "$SSH_USER@$DB_PUB" "$@"; }
scp_db()  { scp "${SSH_OPTS[@]}" "$1" "$SSH_USER@$DB_PUB:$2"; }
scp_app() { scp "${SSH_OPTS[@]}" "$1" "$SSH_USER@$APP_PUB:$2"; }
log()  { printf '\n\033[1;34m▶ %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m! %s\033[0m\n' "$*"; }
confirm() { read -r -p "$(printf '\033[1;33m%s [s/N] \033[0m' "$1")" r; [[ "${r:-}" =~ ^[sS]$ ]]; }

# ---------------- PREP ----------------
prep() {
  log "0/4 — checando acesso às duas VMs"
  ssh_app "echo app-ok >/dev/null && command -v docker >/dev/null" || { echo "SSH/Docker na app falhou"; exit 1; }
  ssh_db  "echo db-ok  >/dev/null && command -v docker >/dev/null" || { echo "SSH/Docker na db falhou"; exit 1; }
  ok "SSH e Docker OK nas duas VMs"

  log "lendo credenciais reais do Postgres em execução na app (não ficam salvas)"
  PGUSER="$(ssh_app "sudo docker exec ebd-postgres printenv POSTGRES_USER" | tr -d '\r' || true)"; PGUSER="${PGUSER:-ebd}"
  PGDB="$(ssh_app   "sudo docker exec ebd-postgres printenv POSTGRES_DB"   | tr -d '\r' || true)"; PGDB="${PGDB:-ebd}"
  PGPW="$(ssh_app   "sudo docker exec ebd-postgres printenv POSTGRES_PASSWORD" | tr -d '\r' || true)"
  [[ -n "$PGPW" ]] || { echo "Não consegui ler POSTGRES_PASSWORD do container ebd-postgres na app."; exit 1; }
  ok "credenciais lidas (user=$PGUSER db=$PGDB)"

  log "1/4 — subindo o Postgres dedicado na VM-db ($DB_PRIV)"
  ssh_db "mkdir -p ~/$DB_DIR"
  scp_db "$REPO_DIR/docker-compose.db.yml" "~/$DB_DIR/docker-compose.db.yml"
  # .env da db (idempotente): mesmas credenciais + bind no IP privado
  ssh_db "cat > ~/$DB_DIR/.env <<EOF
POSTGRES_DB=$PGDB
POSTGRES_USER=$PGUSER
POSTGRES_PASSWORD=$PGPW
EBD_DB_BIND_IP=$DB_PRIV
EOF
chmod 600 ~/$DB_DIR/.env"
  # firewall do host: libera 5432 só do IP privado da app (mesmo padrão do bootstrap 80/443)
  ssh_db "sudo iptables -C INPUT -p tcp -s $APP_PRIV --dport 5432 -j ACCEPT 2>/dev/null \
          || sudo iptables -I INPUT 6 -m state --state NEW -p tcp -s $APP_PRIV --dport 5432 -j ACCEPT; \
          sudo netfilter-persistent save 2>/dev/null || true"
  ssh_db "cd ~/$DB_DIR && sudo docker compose -f docker-compose.db.yml --env-file .env up -d"
  log "aguardando o Postgres ficar pronto..."
  for i in $(seq 1 30); do
    if ssh_db "sudo docker exec ebd-postgres pg_isready -U $PGUSER -d $PGDB" >/dev/null 2>&1; then ok "Postgres pronto na db"; break; fi
    sleep 3; [[ $i -eq 30 ]] && { echo "Postgres não ficou pronto"; exit 1; }
  done

  log "2/4 — liberando 5432 na Security List (origem $APP_PRIV/32 apenas)"
  local bkp="$REPO_DIR/scripts/.seclist-ingress-backup-$(date +%Y%m%d%H%M%S).json"
  oci network security-list get --security-list-id "$SECLIST_ID" \
      --query "data.\"ingress-security-rules\"" > "$bkp"
  ok "regras atuais salvas em $(basename "$bkp")"
  local merged="$REPO_DIR/scripts/.seclist-ingress-new.json"
  python3 - "$bkp" "$merged" "$APP_PRIV" <<'PY'
import json, sys
cur = json.load(open(sys.argv[1])); out, ip = sys.argv[2], sys.argv[3]
def is5432(r):
    t = (r.get("tcp-options") or r.get("tcpOptions") or {})
    dr = (t.get("destination-port-range") or t.get("destinationPortRange") or {})
    return str(r.get("source"))==f"{ip}/32" and dr.get("min")==5432 and dr.get("max")==5432
if any(is5432(r) for r in cur):
    print("regra 5432 já existe — nada a fazer"); json.dump(cur, open(out,"w"))
else:
    cur.append({"protocol":"6","source":f"{ip}/32","isStateless":False,
                "tcpOptions":{"destinationPortRange":{"min":5432,"max":5432}}})
    json.dump(cur, open(out,"w")); print("regra 5432 adicionada")
PY
  oci network security-list update --security-list-id "$SECLIST_ID" \
      --ingress-security-rules "file://$merged" --force >/dev/null
  ok "Security List atualizada (backup em $(basename "$bkp"))"

  log "3/4 — migrando os dados (pg_dump da app -> psql na db, pelo IP privado)"
  ssh_app "set -e; PGPW=\$(sudo docker exec ebd-postgres printenv POSTGRES_PASSWORD); \
    sudo docker exec ebd-postgres pg_dump -U $PGUSER -d $PGDB \
    | sudo docker run -i --rm -e PGPASSWORD=\"\$PGPW\" postgres:16-alpine \
        psql -h $DB_PRIV -U $PGUSER -d $PGDB >/tmp/restore.log 2>&1" \
    && ok "dump restaurado" || { warn "restore retornou erro — veja /tmp/restore.log na app"; }
  # verificação: conta alunos nos dois bancos
  local n_app n_db
  n_app="$(ssh_app "sudo docker exec ebd-postgres psql -U $PGUSER -d $PGDB -tAc 'select count(*) from aluno'" | tr -d '\r' || echo '?')"
  n_db="$(ssh_db  "sudo docker exec ebd-postgres psql -U $PGUSER -d $PGDB -tAc 'select count(*) from aluno'" | tr -d '\r' || echo '?')"
  echo "   alunos: app=$n_app · db=$n_db"
  [[ "$n_app" == "$n_db" && "$n_app" != "?" ]] && ok "contagem confere" || warn "contagens diferentes — confira antes de virar a app"
  ok "PREP concluído (a app ainda usa o banco local — nada quebrou)"
}

# ---------------- CUTOVER (destrutivo) ----------------
cutover() {
  log "4/4 — virando a app para o banco remoto ($DB_PRIV)"
  scp_app "$REPO_DIR/docker-compose.app.yml" "~/$APP_DIR/docker-compose.app.yml"
  # grava EBD_DB_HOST em LINHA PRÓPRIA: remove qualquer ocorrência anterior (inclusive "grudada"
  # no fim de outra linha, quando o .env não terminava com \n) e anexa com quebra de linha à frente.
  ssh_app "cd ~/$APP_DIR && cp .env .env.bak 2>/dev/null || true; sed -i 's/EBD_DB_HOST=[0-9.]*//g' .env; printf '\nEBD_DB_HOST=%s\n' '$DB_PRIV' >> .env"
  # passa EBD_DB_HOST inline também (reforço: independe do parsing do .env)
  ssh_app "cd ~/$APP_DIR && sudo EBD_DB_HOST='$DB_PRIV' docker compose -f docker-compose.app.yml --env-file .env up -d --build"
  log "aguardando o health..."
  for i in $(seq 1 30); do
    if curl -sf "https://ebd-ices.duckdns.org/q/health" >/dev/null 2>&1; then ok "health OK"; break; fi
    sleep 4; [[ $i -eq 30 ]] && warn "health não respondeu — veja os logs do backend"
  done
  ssh_app "cd ~/$APP_DIR && sudo docker compose -f docker-compose.app.yml exec -T caddy caddy reload --config /etc/caddy/Caddyfile || true"

  if confirm "Backend no ar contra o banco remoto. Remover o Postgres LOCAL da app agora?"; then
    ssh_app "sudo docker rm -f ebd-postgres" && ok "Postgres local removido (volume ebd_pgdata fica até você apagar)"
  else
    warn "Postgres local mantido. Remova depois com: ssh $SSH_USER@$APP_PUB 'sudo docker rm -f ebd-postgres'"
  fi

  log "verificação final"
  ssh_app "sudo docker logs --tail 15 ebd-backend | grep -iE 'flyway|listening|started' || true"
  ok "Cutover concluído. 5432 só responde do IP privado da app (teste 'nc -vz $DB_PUB 5432' de fora → deve falhar)."
}

MODE="${1:-auto}"
case "$MODE" in
  prep)    prep ;;
  cutover) cutover ;;
  auto)
    prep
    echo
    if confirm "PREP ok. Virar a app para o banco remoto AGORA? (há ~1-2 min de indisponibilidade)"; then
      cutover
    else
      warn "Parando após o PREP. Quando quiser virar: ./scripts/estagio2-cutover.sh cutover"
    fi
    ;;
  *) echo "uso: $0 [prep|cutover]"; exit 1 ;;
esac
