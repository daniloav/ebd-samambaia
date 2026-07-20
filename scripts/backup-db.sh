#!/usr/bin/env bash
# ============================================================
# Backup do banco de PRODUÇÃO antes do deploy. Roda NA VM (via SSH pelo CD),
# a partir de ~/ebd-samambaia, ANTES do `docker compose up -d --build`
# (ou seja, antes das migrations do Flyway rodarem).
#
# - Faz pg_dump do container `db` e guarda em backups/ebd-AAAAMMDD-HHMMSS.sql.gz
# - Valida que o dump não saiu vazio/corrompido (senão ABORTA o deploy — melhor
#   parar do que subir migration sem ponto de restauração).
# - Retenção: mantém os 10 backups mais recentes.
# - No 1º deploy (container `db` ainda não existe) apenas pula, sem falhar.
#
# RESTAURAR um backup (na VM, em ~/ebd-samambaia):
#   gzip -dc backups/ebd-AAAAMMDD-HHMMSS.sql.gz \
#     | docker compose exec -T db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
# ============================================================
set -euo pipefail
cd "$(dirname "$0")/.."   # raiz do app na VM (~/ebd-samambaia)

mkdir -p backups

if [ -z "$(docker compose ps -q db 2>/dev/null)" ]; then
  echo "→ Container 'db' não está rodando (primeiro deploy?) — pulando backup."
  exit 0
fi

TS="$(date +%Y%m%d-%H%M%S)"
OUT="backups/ebd-${TS}.sql.gz"
echo "→ Gerando backup do banco: ${OUT}"

# pg_dump roda DENTRO do container, usando as credenciais do próprio container.
docker compose exec -T db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  | gzip > "$OUT"

# Valida: arquivo não-vazio e gzip íntegro.
if [ ! -s "$OUT" ] || ! gzip -t "$OUT" 2>/dev/null; then
  echo "✗ ERRO: backup vazio/corrompido — ABORTANDO o deploy (sem ponto de restauração)."
  rm -f "$OUT"
  exit 1
fi

echo "✓ Backup OK ($(du -h "$OUT" | cut -f1))."

# Retenção: mantém os 10 mais recentes, remove o resto.
ls -1t backups/ebd-*.sql.gz 2>/dev/null | tail -n +11 | xargs -r rm -f
echo "→ Backups atuais:"
ls -1t backups/ebd-*.sql.gz 2>/dev/null | head -10
