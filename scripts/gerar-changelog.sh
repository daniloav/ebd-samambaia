#!/usr/bin/env bash
# ============================================================
# Gera notas de changelog a partir dos Conventional Commits.
#
# Agrupa os commits (subject) em seções PT-BR e ignora ruído
# (chore/docs/test/style/build e os "chore(release)" do CD).
#
# Modos:
#   notas <vX.Y.Z>   Corpo markdown da seção de UMA versão (commits da tag
#                    anterior até vX.Y.Z). Usado pelo CD p/ o GitHub Release.
#   preview          Seção "[Não lançado]" (última tag..HEAD) no stdout.
#   secao            Igual ao preview, mas SEM o cabeçalho "## [...]"
#                    (só as subseções) — útil p/ colar no CHANGELOG.md.
#
# Exemplos:
#   bash scripts/gerar-changelog.sh notas v1.25.0
#   bash scripts/gerar-changelog.sh preview
# ============================================================
set -euo pipefail
cd "$(dirname "$0")/.."

MODE="${1:-preview}"

# --- Descobre o intervalo de commits (range) e o título ---------------------
tag_anterior() {  # imprime a tag imediatamente abaixo de $1 (ordem SemVer)
  git tag -l 'v[0-9]*' --sort=v:refname | awk -v t="$1" '$0==t{print p} {p=$0}'
}
raiz() { git rev-list --max-parents=0 HEAD | tail -n1; }

case "$MODE" in
  notas)
    ALVO="${2:?uso: gerar-changelog.sh notas vX.Y.Z}"
    PREV="$(tag_anterior "$ALVO")"
    DE="${PREV:-$(raiz)}"
    RANGE="${DE}..${ALVO}"
    HEADER="## ${ALVO}"
    ;;
  preview|secao)
    PREV="$(git tag -l 'v[0-9]*' --sort=-v:refname | head -n1 || true)"
    DE="${PREV:-$(raiz)}"
    RANGE="${DE}..HEAD"
    HEADER="## [Não lançado]"
    ;;
  *) echo "Modo inválido: $MODE (use: notas <tag> | preview | secao)" >&2; exit 2 ;;
esac

# --- Coleta e classifica os commits -----------------------------------------
declare -a ADD=() FIX=() CHG=() INF=() OTH=()

add_bullet() {  # $1=tipo  $2=escopo  $3=descrição
  local b="- "
  [[ -n "$2" ]] && b+="**${2}:** "
  b+="$3"
  case "$1" in
    feat)            ADD+=("$b") ;;
    fix)             FIX+=("$b") ;;
    refactor|perf)   CHG+=("$b") ;;
    infra|ci|build)  INF+=("$b") ;;
    *)               OTH+=("$b") ;;
  esac
}

while IFS= read -r s; do
  [[ -z "$s" ]] && continue
  if [[ "$s" =~ ^([a-zA-Z]+)(\(([^\)]*)\))?(!)?:[[:space:]]*(.*)$ ]]; then
    tipo="${BASH_REMATCH[1],,}"
    escopo="${BASH_REMATCH[3]}"
    desc="${BASH_REMATCH[5]}"
    # ruído que não entra no changelog
    case "$tipo" in chore|docs|test|style) continue ;; esac
    add_bullet "$tipo" "$escopo" "$desc"
  else
    # commit fora do padrão conventional (ex.: "Add justificativas de faltas")
    OTH+=("- $s")
  fi
done < <(git log --no-merges --format='%s' "$RANGE")

# --- Emite o markdown --------------------------------------------------------
emite_secao() {  # $1=título  $2..=bullets
  local titulo="$1"; shift
  (( $# == 0 )) && return 0
  printf '### %s\n' "$titulo"
  printf '%s\n' "$@"
  printf '\n'
}

[[ "$MODE" != "secao" ]] && { printf '%s\n\n' "$HEADER"; }

emite_secao "Adicionado" ${ADD[@]+"${ADD[@]}"}
emite_secao "Corrigido"  ${FIX[@]+"${FIX[@]}"}
emite_secao "Alterado"   ${CHG[@]+"${CHG[@]}"}
emite_secao "Infra"      ${INF[@]+"${INF[@]}"}
emite_secao "Outros"     ${OTH[@]+"${OTH[@]}"}

# Se nada foi classificado, deixa um aviso (evita release vazio silencioso).
if (( ${#ADD[@]} + ${#FIX[@]} + ${#CHG[@]} + ${#INF[@]} + ${#OTH[@]} == 0 )); then
  printf '_Sem mudanças relevantes para o changelog neste intervalo._\n'
fi
