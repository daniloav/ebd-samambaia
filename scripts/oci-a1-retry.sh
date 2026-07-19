#!/usr/bin/env bash
# ============================================================
# Retry automático para lançar a VM A1.Flex (Always Free) na
# Oracle Cloud, contornando o erro "Out of capacity".
#
# Configuração fica em scripts/.oci-launch.env (NÃO versionado).
#   cp scripts/.oci-launch.env.example scripts/.oci-launch.env  (e preencha)
# Depois execute:   ./oci-a1-retry.sh
# Deixe rodando; ele tenta até conseguir capacidade.
# ============================================================
set -uo pipefail

# ================= CONFIG (arquivo local) ===================
# Os valores ficam em scripts/.oci-launch.env, que NÃO é versionado,
# para não expor OCIDs no repositório.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.oci-launch.env"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "❌ Configuração não encontrada: $ENV_FILE"
  echo "   Crie a partir do exemplo e preencha:"
  echo "   cp scripts/.oci-launch.env.example scripts/.oci-launch.env"
  exit 1
fi
# shellcheck source=/dev/null
source "$ENV_FILE"

# padrões (sobrescreva no .oci-launch.env se quiser)
SSH_KEY_FILE="${SSH_KEY_FILE:-$HOME/.ssh/id_ed25519.pub}"
DISPLAY_NAME="${DISPLAY_NAME:-ebd-server}"
OCPUS="${OCPUS:-1}"
MEM_GB="${MEM_GB:-6}"
SLEEP_SECONDS="${SLEEP_SECONDS:-60}"
# ============================================================

ERR_LOG="$(mktemp)"
trap 'rm -f "$ERR_LOG"' EXIT

# validação básica: obrigatórios preenchidos
for v in COMPARTMENT_ID SUBNET_ID IMAGE_ID; do
  if [[ -z "${!v:-}" || "${!v}" == COLE_AQUI* ]]; then
    echo "❌ Defina $v em $ENV_FILE (use ./scripts/oci-descobrir.sh)."
    exit 1
  fi
done
if [[ -z "${ADS[0]:-}" || "${ADS[0]}" == COLE_AQUI* ]]; then
  echo "❌ Defina o array ADS em $ENV_FILE."
  exit 1
fi

attempt=0
idx=0
echo "🚀 Iniciando tentativas de criar '$DISPLAY_NAME' (A1.Flex ${OCPUS} OCPU / ${MEM_GB}GB)"
echo "   Pressione Ctrl+C para parar. Log de erro em: $ERR_LOG"
echo

while true; do
  attempt=$((attempt + 1))
  AD="${ADS[$((idx % ${#ADS[@]}))]}"
  idx=$((idx + 1))

  echo "[$(date '+%d/%m %H:%M:%S')] Tentativa #$attempt — AD: $AD"

  if oci compute instance launch \
      --compartment-id "$COMPARTMENT_ID" \
      --availability-domain "$AD" \
      --shape "VM.Standard.A1.Flex" \
      --shape-config "{\"ocpus\":${OCPUS},\"memoryInGBs\":${MEM_GB}}" \
      --image-id "$IMAGE_ID" \
      --subnet-id "$SUBNET_ID" \
      --assign-public-ip true \
      --display-name "$DISPLAY_NAME" \
      --ssh-authorized-keys-file "$SSH_KEY_FILE" \
      --wait-for-state RUNNING \
      2> "$ERR_LOG"; then
    echo
    echo "✅ SUCESSO! Instância criada e em execução."
    echo "   Veja o IP público no console (Compute → Instances → $DISPLAY_NAME)"
    echo "   ou rode: oci compute instance list-vnics --instance-id <ID> --query 'data[0].\"public-ip\"'"
    exit 0
  fi

  # Aborta apenas em erros REAIS de config/permissão/quota.
  # Capacidade, timeouts de rede, throttling e 5xx são transitórios -> continua tentando.
  if grep -qiE "NotAuthenticated|NotAuthorized|not authorized|InvalidParameter|Invalid.*[Pp]arameter|LimitExceeded|QuotaExceeded|already exists|CannotParseRequest" "$ERR_LOG"; then
    echo "   ❌ Erro de configuração/permissão — precisa corrigir:"
    echo "   ----------------------------------------------------"
    cat "$ERR_LOG"
    echo "   ----------------------------------------------------"
    exit 1
  fi

  if grep -qiE "capacity" "$ERR_LOG"; then
    echo "   ⏳ Sem capacidade. Nova tentativa em ${SLEEP_SECONDS}s..."
  else
    echo "   ⚠️  Erro transitório (rede/timeout/throttling). Nova tentativa em ${SLEEP_SECONDS}s..."
  fi
  sleep "$SLEEP_SECONDS"
done
