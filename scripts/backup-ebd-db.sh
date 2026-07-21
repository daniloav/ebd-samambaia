#!/usr/bin/env bash
# ============================================================
# Backup do Postgres da EBD — roda NA VM ebd-db (chamado pelo cron).
# Dump do container ebd-postgres + gzip + rotação (mantém os N mais recentes).
#
# RESTAURAR um backup (na ebd-db):
#   gzip -dc ~/backups/ebd-AAAAMMDD-HHMM.sql.gz \
#     | docker exec -i ebd-postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
# ============================================================
set -euo pipefail
DIR="${BACKUP_DIR:-$HOME/backups}"
RETAIN="${RETAIN:-14}"           # quantos backups manter
mkdir -p "$DIR"

if ! docker inspect ebd-postgres >/dev/null 2>&1; then
  echo "$(date '+%F %T') container ebd-postgres não encontrado — pulando."; exit 0
fi

TS="$(date +%Y%m%d-%H%M)"
OUT="$DIR/ebd-${TS}.sql.gz"
# pg_dump DENTRO do container, com as credenciais do próprio container.
docker exec ebd-postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' | gzip > "$OUT"

# valida: não-vazio e gzip íntegro (senão remove e falha)
if [ ! -s "$OUT" ] || ! gzip -t "$OUT" 2>/dev/null; then
  echo "$(date '+%F %T') ERRO: backup vazio/corrompido: $OUT"; rm -f "$OUT"; exit 1
fi
echo "$(date '+%F %T') backup OK: $OUT ($(du -h "$OUT" | cut -f1))"

# Offsite (opcional): envia para o OCI Object Storage via PAR write-only.
# A URL fica em ~/.ebd-backup-par (instalada por scripts/setup-offsite-oci.sh). Falha aqui NÃO
# derruba o backup local — só avisa.
PAR_FILE="${PAR_FILE:-$HOME/.ebd-backup-par}"
PAR_URL="${PAR_URL:-$([ -f "$PAR_FILE" ] && cat "$PAR_FILE" || true)}"
if [ -n "${PAR_URL:-}" ]; then
  if curl -fsS -T "$OUT" "${PAR_URL}$(basename "$OUT")" >/dev/null 2>&1; then
    echo "$(date '+%F %T') offsite OK: $(basename "$OUT") -> Object Storage"
  else
    echo "$(date '+%F %T') AVISO: upload offsite falhou (backup local mantido)"
  fi
fi

# retenção: mantém os $RETAIN mais recentes
ls -1t "$DIR"/ebd-*.sql.gz 2>/dev/null | tail -n +$((RETAIN + 1)) | xargs -r rm -f
