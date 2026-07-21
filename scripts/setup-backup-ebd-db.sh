#!/usr/bin/env bash
# ============================================================
# Instala o backup AGENDADO do Postgres na VM ebd-db. RODA NO SEU MAC (via SSH).
# Copia scripts/backup-ebd-db.sh para a VM, agenda no cron (diário) e faz 1 backup de teste.
#
# Uso:            ./scripts/setup-backup-ebd-db.sh
# Personalizar:   BACKUP_HOUR=4 RETAIN=14 ./scripts/setup-backup-ebd-db.sh
# ============================================================
set -euo pipefail
DB_PUB="136.248.80.0"                 # ebd-db — IP público
SSH_USER="ubuntu"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_ed25519}"
BACKUP_HOUR="${BACKUP_HOUR:-4}"       # hora na VM (UTC); 4 UTC ~ 01:00 BRT (horário calmo)
RETAIN="${RETAIN:-14}"                # backups a manter
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SSH_OPTS=(-o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 -i "$SSH_KEY")
ssh_db() { ssh "${SSH_OPTS[@]}" "$SSH_USER@$DB_PUB" "$@"; }

echo "▶ Copiando o script de backup para a ebd-db..."
scp "${SSH_OPTS[@]}" "$REPO_DIR/scripts/backup-ebd-db.sh" "$SSH_USER@$DB_PUB:~/backup-ebd-db.sh"
ssh_db "chmod +x ~/backup-ebd-db.sh && mkdir -p ~/backups"

echo "▶ Agendando no cron (diário às ${BACKUP_HOUR}:00 UTC, retendo ${RETAIN})..."
CRON_LINE="0 ${BACKUP_HOUR} * * * RETAIN=${RETAIN} /home/${SSH_USER}/backup-ebd-db.sh >> /home/${SSH_USER}/backups/backup.log 2>&1"
ssh_db "( crontab -l 2>/dev/null | grep -v 'backup-ebd-db.sh' ; echo '${CRON_LINE}' ) | crontab -"
echo "  cron atual:"; ssh_db "crontab -l | grep backup-ebd-db.sh"

echo "▶ Rodando um backup de TESTE agora..."
ssh_db "RETAIN=${RETAIN} ~/backup-ebd-db.sh"
echo "▶ Backups na ebd-db:"; ssh_db "ls -lh ~/backups/ | tail -5"

cat <<TIP

✓ Pronto — backup diário às ${BACKUP_HOUR}:00 UTC em ~/backups na ebd-db (mantém ${RETAIN}).
  Offsite (recomendado de vez em quando, do seu Mac):
    mkdir -p backups-ebd && scp -i ${SSH_KEY} "${SSH_USER}@${DB_PUB}:~/backups/ebd-*.sql.gz" backups-ebd/
TIP
