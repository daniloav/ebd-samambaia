#!/usr/bin/env bash
# ============================================================
# Offsite dos backups no OCI Object Storage (Always Free: 10 GB). RODA NO SEU MAC.
# Cria o bucket + política de expiração, gera um PAR *write-only* (validade 1 ano),
# instala a URL na ebd-db e roda um upload de teste.
#
# Rode DEPOIS do setup-backup-ebd-db.sh (que instala o cron). Uso:
#   ./scripts/setup-offsite-oci.sh
#   BUCKET=ebd-backups OFFSITE_DIAS=30 ./scripts/setup-offsite-oci.sh
#
# Segurança: a VM só recebe uma URL que SÓ escreve (não lê, não lista, não apaga) e expira.
# ============================================================
set -euo pipefail
DB_PUB="136.248.80.0"
SSH_USER="ubuntu"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_ed25519}"
BUCKET="${BUCKET:-ebd-backups}"
OFFSITE_DIAS="${OFFSITE_DIAS:-30}"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SSH_OPTS=(-o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 -i "$SSH_KEY")
ssh_db() { ssh "${SSH_OPTS[@]}" "$SSH_USER@$DB_PUB" "$@"; }

# COMPARTMENT_ID vem do .oci-launch.env
[ -f "$REPO_DIR/scripts/.oci-launch.env" ] && source "$REPO_DIR/scripts/.oci-launch.env"
: "${COMPARTMENT_ID:?defina COMPARTMENT_ID (scripts/.oci-launch.env)}"

REGION="${OCI_REGION:-$(awk -F= '/^region/{gsub(/ /,"",$2);print $2;exit}' ~/.oci/config 2>/dev/null || true)}"
REGION="${REGION:-sa-saopaulo-1}"
NS="$(oci os ns get --query data --raw-output)"
echo "▶ Namespace: $NS · Região: $REGION · Bucket: $BUCKET"

echo "▶ Garantindo o bucket (privado)..."
if ! oci os bucket get --namespace "$NS" --bucket-name "$BUCKET" >/dev/null 2>&1; then
  oci os bucket create --compartment-id "$COMPARTMENT_ID" --namespace "$NS" \
    --name "$BUCKET" --public-access-type NoPublicAccess >/dev/null
  echo "  bucket criado."
else
  echo "  bucket já existe."
fi

echo "▶ Política de expiração offsite: apaga objetos com mais de ${OFFSITE_DIAS} dias..."
oci os object-lifecycle-policy put --namespace "$NS" --bucket-name "$BUCKET" --force --items \
  "[{\"action\":\"DELETE\",\"isEnabled\":true,\"name\":\"expira-antigos\",\"objectNameFilter\":{\"inclusionPrefixes\":[\"ebd-\"]},\"timeAmount\":${OFFSITE_DIAS},\"timeUnit\":\"DAYS\"}]" \
  >/dev/null 2>&1 && echo "  política aplicada." || echo "  ! não consegui aplicar a política (siga sem ela; configure depois no console)."

echo "▶ Gerando PAR write-only (validade 1 ano)..."
EXPIRES="$(date -u -v+1y +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '+1 year' +%Y-%m-%dT%H:%M:%SZ)"
ACCESS_URI="$(oci os preauth-request create --namespace "$NS" --bucket-name "$BUCKET" \
  --name "ebd-db-offsite-$(date +%Y%m%d)" --access-type AnyObjectWrite \
  --time-expires "$EXPIRES" --query 'data."access-uri"' --raw-output)"
PAR_URL="https://objectstorage.${REGION}.oraclecloud.com${ACCESS_URI}"
echo "  PAR criado (expira em $EXPIRES)."

echo "▶ Instalando na ebd-db: script de backup atualizado + URL do PAR..."
scp "${SSH_OPTS[@]}" "$REPO_DIR/scripts/backup-ebd-db.sh" "$SSH_USER@$DB_PUB:~/backup-ebd-db.sh"
ssh_db "chmod +x ~/backup-ebd-db.sh"
printf '%s' "$PAR_URL" | ssh_db "cat > ~/.ebd-backup-par && chmod 600 ~/.ebd-backup-par"

echo "▶ Upload de teste (roda um backup, que agora também envia offsite)..."
ssh_db "~/backup-ebd-db.sh"
echo "▶ Objetos no bucket:"
oci os object list --namespace "$NS" --bucket-name "$BUCKET" --query 'data[].name' --output table 2>/dev/null || true

cat <<TIP

✓ Offsite ativo: cada backup vai para o bucket '$BUCKET' (retém ${OFFSITE_DIAS} dias).
  ⏰ O PAR expira em ${EXPIRES} — reexecute este script antes disso para renovar.
  Ver/baixar objetos:  oci os object list  --namespace $NS --bucket-name $BUCKET
                       oci os object get   --namespace $NS --bucket-name $BUCKET --name ebd-AAAAMMDD-HHMM.sql.gz --file ./restore.sql.gz
TIP
