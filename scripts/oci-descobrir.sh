#!/usr/bin/env bash
# Descobre os OCIDs necessários para lançar a instância A1.Flex.
# Uso:  ./oci-descobrir.sh
# Pré-requisito: OCI CLI instalada e configurada (oci setup config).

set -euo pipefail

echo "========================================================"
echo " 1) TENANCY (pode ser usada como compartment raiz)"
echo "========================================================"
TENANCY_ID=$(oci iam compartment list --query 'data[0]."compartment-id"' --raw-output 2>/dev/null || true)
if [ -z "${TENANCY_ID:-}" ]; then
  # fallback: pega do arquivo de config
  TENANCY_ID=$(grep -E '^tenancy=' ~/.oci/config | head -1 | cut -d= -f2)
fi
echo "TENANCY / COMPARTMENT raiz:"
echo "  $TENANCY_ID"
echo

echo "========================================================"
echo " 2) COMPARTMENTS (escolha onde criar; ou use a tenancy acima)"
echo "========================================================"
oci iam compartment list \
  --compartment-id "$TENANCY_ID" \
  --query 'data[].{nome:name, id:id}' --output table 2>/dev/null || echo "(sem subcompartments)"
echo

echo "========================================================"
echo " 3) AVAILABILITY DOMAINS (use estes nomes no ADS do retry)"
echo "========================================================"
oci iam availability-domain list \
  --compartment-id "$TENANCY_ID" \
  --query 'data[].name' --output table
echo

echo "========================================================"
echo " 4) IMAGEM Ubuntu 22.04 para ARM (A1.Flex / aarch64)"
echo "========================================================"
oci compute image list \
  --compartment-id "$TENANCY_ID" \
  --operating-system "Canonical Ubuntu" \
  --operating-system-version "22.04" \
  --shape "VM.Standard.A1.Flex" \
  --sort-by TIMECREATED --sort-order DESC \
  --query 'data[0].{imagem:"display-name", id:id}' --output table
echo

echo "========================================================"
echo " 5) SUBNETS (use a que for PUBLIC subnet)"
echo "========================================================"
echo ">> Rode com o compartment onde está sua VCN, ex:"
echo "   oci network subnet list --compartment-id $TENANCY_ID --query 'data[].{nome:\"display-name\", publica:\"prohibit-public-ip-on-vnic\", id:id}' --output table"
oci network subnet list \
  --compartment-id "$TENANCY_ID" \
  --query 'data[].{nome:"display-name", proibe_ip_publico:"prohibit-public-ip-on-vnic", id:id}' \
  --output table 2>/dev/null || echo "(nenhuma subnet neste compartment — ajuste o --compartment-id)"
echo
echo "Obs.: na coluna 'proibe_ip_publico', a subnet PÚBLICA mostra 'False'."
