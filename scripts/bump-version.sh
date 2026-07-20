#!/usr/bin/env bash
# ============================================================
# Calcula a PRÓXIMA versão (SemVer) a partir dos commits desde a última tag v*:
#   - "BREAKING CHANGE" ou "tipo!:"  -> MAJOR
#   - "feat:" / "feat(x):"           -> MINOR
#   - qualquer outro (fix/docs/...)  -> PATCH
# Grava a nova versão em frontend/src/app/version.ts (NÃO toca no package.json, para não
# invalidar o cache do `npm ci` no build do Docker).
# e imprime a nova versão no stdout. Na primeira execução (sem nenhuma tag),
# usa a versão atual do package.json como baseline (sem incrementar).
#
# Uso: bash scripts/bump-version.sh   (roda no CD, antes do build/rsync)
# ============================================================
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION_TS="frontend/src/app/version.ts"
PKG="frontend/package.json"

pkg_version() {
  grep -m1 '"version"' "$PKG" | sed -E 's/.*"version"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/'
}

LAST_TAG="$(git tag -l 'v[0-9]*' --sort=-v:refname | head -n1 || true)"

if [ -z "$LAST_TAG" ]; then
  # Primeira vez: estabelece a versão atual como baseline, sem incrementar.
  NEW="$(pkg_version)"
else
  BASE="${LAST_TAG#v}"
  MSGS="$(git log "${LAST_TAG}..HEAD" --no-merges --pretty=%B 2>/dev/null || true)"
  if printf '%s' "$MSGS" | grep -qiE 'BREAKING CHANGE|^[a-z]+(\([^)]*\))?!:'; then
    LEVEL="major"
  elif printf '%s' "$MSGS" | grep -qiE '^feat(\([^)]*\))?:'; then
    LEVEL="minor"
  else
    LEVEL="patch"
  fi
  IFS='.' read -r MA MI PA <<< "$BASE"
  case "$LEVEL" in
    major) MA=$((MA + 1)); MI=0; PA=0 ;;
    minor) MI=$((MI + 1)); PA=0 ;;
    patch) PA=$((PA + 1)) ;;
  esac
  NEW="${MA}.${MI}.${PA}"
fi

# Grava a nova versão nos dois arquivos.
sed -i.bak -E "s/export const APP_VERSION = '[^']*';/export const APP_VERSION = '${NEW}';/" "$VERSION_TS" && rm -f "${VERSION_TS}.bak"

printf '%s\n' "$NEW"
