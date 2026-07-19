#!/usr/bin/env bash
# Gera o par de chaves RSA que o backend usa para assinar/verificar os tokens JWT.
# Rode UMA vez antes de subir o backend (dev ou build), pois as chaves NÃO são
# versionadas no Git (ficam no .gitignore).
#
# Uso:  ./scripts/gen-jwt-keys.sh
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../backend/src/main/resources" && pwd)"
PRIV="$DIR/privateKey.pem"
PUB="$DIR/publicKey.pem"

if [[ -f "$PRIV" && -f "$PUB" && "${1:-}" != "--force" ]]; then
  echo "✔ Chaves já existem em $DIR (use --force para regenerar)."
  exit 0
fi

echo "▶ Gerando par de chaves RSA em $DIR ..."
openssl genrsa -out "$PRIV" 2048
openssl rsa -in "$PRIV" -pubout -out "$PUB"
chmod 600 "$PRIV"
echo "✅ Chaves geradas:"
echo "   $PRIV (privada — NÃO versionar)"
echo "   $PUB  (pública)"
