#!/usr/bin/env bash
# ============================================================
# Sobe TODO o ambiente de desenvolvimento local com um comando:
#   PostgreSQL (nativo) + Backend (Quarkus) + Frontend (Angular)
#
# Uso:    ./scripts/dev-up.sh
# Parar:  ./scripts/dev-down.sh
#
# É idempotente: pode rodar quantas vezes quiser. Instala o que faltar,
# cria o banco se não existir e não duplica serviços já no ar.
# (Usamos Postgres nativo porque o Docker não funciona neste Mac.)
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PGBIN=/opt/homebrew/opt/postgresql@16/bin
BACK_LOG=/tmp/ebd-backend.log
FRONT_LOG=/tmp/ebd-frontend.log

log(){ printf "\n\033[1;34m▶ %s\033[0m\n" "$*"; }

porta_uso(){ lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

# ---------- 1) PostgreSQL ----------
if [ ! -x "$PGBIN/psql" ]; then
  log "Instalando PostgreSQL 16 (Homebrew)..."
  brew install postgresql@16
fi

log "Garantindo o PostgreSQL no ar..."
brew services start postgresql@16 >/dev/null || true
for _ in $(seq 1 30); do
  if "$PGBIN/pg_isready" -h localhost -p 5432 >/dev/null 2>&1; then break; fi
  sleep 1
done

log "Garantindo role e database 'ebd'..."
if ! "$PGBIN/psql" -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname='ebd'" | grep -q 1; then
  "$PGBIN/psql" -d postgres -c "CREATE ROLE ebd WITH LOGIN PASSWORD 'ebd';"
fi
if ! "$PGBIN/psql" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='ebd'" | grep -q 1; then
  "$PGBIN/psql" -d postgres -c "CREATE DATABASE ebd OWNER ebd;"
fi

# ---------- 2) Chaves JWT ----------
log "Garantindo as chaves JWT..."
"$ROOT/scripts/gen-jwt-keys.sh"

# ---------- 3) Dependências do frontend ----------
if [ ! -d "$ROOT/frontend/node_modules" ]; then
  log "Instalando dependências do frontend (npm ci)..."
  ( cd "$ROOT/frontend" && npm ci )
fi

# ---------- 4) Backend ----------
if porta_uso 8080; then
  log "Backend já está no ar (porta 8080) — não subo de novo."
else
  log "Subindo o backend (Quarkus dev) → log em $BACK_LOG"
  ( cd "$ROOT/backend" && nohup mvn quarkus:dev -Dquarkus.analytics.disabled=true > "$BACK_LOG" 2>&1 & )
fi

# ---------- 5) Frontend ----------
if porta_uso 4200; then
  log "Frontend já está no ar (porta 4200) — não subo de novo."
else
  log "Subindo o frontend (Angular) → log em $FRONT_LOG"
  ( cd "$ROOT/frontend" && nohup npm start > "$FRONT_LOG" 2>&1 & )
fi

# ---------- 6) Aguardar ficarem prontos ----------
log "Aguardando o backend (na 1ª vez baixa dependências, pode levar ~40s)..."
for _ in $(seq 1 80); do
  if curl -fsS http://localhost:8080/q/health 2>/dev/null | grep -q UP; then break; fi
  sleep 3
done

log "Aguardando o frontend..."
for _ in $(seq 1 60); do
  if curl -fsS http://localhost:4200/ >/dev/null 2>&1; then break; fi
  sleep 2
done

# ---------- 7) Status final ----------
echo
echo "============================================================"
if curl -fsS http://localhost:8080/q/health 2>/dev/null | grep -q UP; then
  echo "✅ Backend   http://localhost:8080   (Swagger: /q/swagger-ui)"
else
  echo "⚠️  Backend não respondeu ainda — veja: tail -f $BACK_LOG"
fi
if curl -fsS http://localhost:4200/ >/dev/null 2>&1; then
  echo "✅ Frontend  http://localhost:4200"
else
  echo "⚠️  Frontend não respondeu ainda — veja: tail -f $FRONT_LOG"
fi
echo "------------------------------------------------------------"
echo "Login:  admin / admin123   (ou professor / prof123)"
echo "Logs:   tail -f $BACK_LOG   |   tail -f $FRONT_LOG"
echo "Parar:  ./scripts/dev-down.sh"
echo "============================================================"
