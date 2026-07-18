#!/usr/bin/env bash
# ============================================================
# Retry automático para lançar a VM A1.Flex (Always Free) na
# Oracle Cloud, contornando o erro "Out of capacity".
#
# Preencha a seção CONFIG abaixo (rode antes o ./oci-descobrir.sh)
# e execute:   ./oci-a1-retry.sh
# Deixe rodando; ele tenta até conseguir capacidade.
# ============================================================
set -uo pipefail

# ======================= CONFIG =============================
COMPARTMENT_ID="ocid1.tenancy.oc1..aaaaaaaaztcw5svgyo77oav5ci3rfiuyd3fkc35qloskxrq3sx2syzdmxgdq"
SUBNET_ID="ocid1.subnet.oc1.sa-saopaulo-1.aaaaaaaakermpppvs6gkppcozrebcpnxmzrrsp2jm3sdlsu52dqsxw5htiwq"  # subnet PÚBLICA
IMAGE_ID="ocid1.image.oc1.sa-saopaulo-1.aaaaaaaaemf52b7af7ncncxz6pdc6hrlkdmylvwejfzpwnpbuhlfxwhrno6a"  # Ubuntu 22.04 ARM
SSH_KEY_FILE="$HOME/.ssh/id_ed25519.pub"    # sua chave pública
DISPLAY_NAME="ebd-server"

OCPUS=1          # 1 OCPU costuma achar capacidade mais fácil
MEM_GB=6         # 6 GB é folgado para este projeto

# Availability Domains a alternar. Cole os nomes reais do oci-descobrir.sh.
# Em regiões com 1 AD, deixe só uma. Ex.:
# ADS=("abcd:SA-SAOPAULO-1-AD-1")
ADS=("xiXO:SA-SAOPAULO-1-AD-1")

SLEEP_SECONDS=60 # espera entre tentativas
# ============================================================

ERR_LOG="$(mktemp)"
trap 'rm -f "$ERR_LOG"' EXIT

# validação básica
for v in COMPARTMENT_ID SUBNET_ID IMAGE_ID; do
  if [[ "${!v}" == COLE_AQUI* ]]; then
    echo "❌ Preencha a variável $v no topo do script (use ./oci-descobrir.sh)."
    exit 1
  fi
done
if [[ "${ADS[0]}" == COLE_AQUI* ]]; then
  echo "❌ Preencha o array ADS com o(s) nome(s) da(s) Availability Domain(s)."
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

  if grep -qiE "out of (host )?capacity|capacity|500" "$ERR_LOG"; then
    echo "   ⏳ Sem capacidade. Nova tentativa em ${SLEEP_SECONDS}s..."
    sleep "$SLEEP_SECONDS"
  else
    echo "   ❌ Erro que NÃO é de capacidade — verifique e corrija:"
    echo "   ----------------------------------------------------"
    cat "$ERR_LOG"
    echo "   ----------------------------------------------------"
    exit 1
  fi
done
