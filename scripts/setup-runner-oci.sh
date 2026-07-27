#!/usr/bin/env bash
# ============================================================================
# setup-runner-oci.sh
#
# Sobe um GitHub Actions self-hosted runner em CONTAINER Docker, numa VM da OCI,
# a partir do binário OFICIAL do runner (nada de imagem de terceiros). O container
# tem acesso ao Docker do host (monta /var/run/docker.sock) para buildar imagens.
#
# RODE NA VM QUE SERÁ O RUNNER (não é a VM do app; ela precisa de RAM p/ buildar —
# veja docs/self-hosted-runner.md). Precisa de Docker instalado e sudo.
#
# Modos:
#   • Persistente (default): registra 1 vez com um TOKEN DE REGISTRO (curto) e o
#     container reinicia sozinho reaproveitando o registro.
#   • Efêmero (--ephemeral): 1 job por execução; para re-registrar a cada job ele
#     precisa de um PAT (--pat) para gerar tokens novos automaticamente.
#
# Uso:
#   ./setup-runner-oci.sh --repo daniloav/ebd-samambaia --token <REG_TOKEN>
#   ./setup-runner-oci.sh --repo daniloav/ebd-samambaia --pat <PAT> --ephemeral
#
# Flags:
#   --repo <owner/repo>     repositório (obrigatório)
#   --token <REG_TOKEN>     token de registro (Settings→Actions→Runners→New; ~1h)
#   --pat <PAT>             Personal Access Token com escopo repo (só p/ --ephemeral)
#   --name <nome>           nome do runner        (default: oci-<hostname>)
#   --labels <a,b,c>        labels                (default: self-hosted,linux,oci)
#   --ephemeral             1 job por execução (exige --pat)
#   --runner-version <x>    versão do runner      (default: 2.319.1)
#   -h | --help
# ============================================================================
set -euo pipefail

REPO=""; REG_TOKEN=""; PAT=""; NAME="oci-$(hostname -s 2>/dev/null || hostname)"
LABELS="self-hosted,linux,oci"; EPHEMERAL="false"; RUNNER_VERSION="2.319.1"

err(){ echo "ERRO: $*" >&2; exit 1; }
info(){ echo -e "\033[1;36m›\033[0m $*"; }
ok(){ echo -e "\033[1;32m✓\033[0m $*"; }
warn(){ echo -e "\033[1;33m!\033[0m $*"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) REPO="${2:-}"; shift 2;;
    --token) REG_TOKEN="${2:-}"; shift 2;;
    --pat) PAT="${2:-}"; shift 2;;
    --name) NAME="${2:-}"; shift 2;;
    --labels) LABELS="${2:-}"; shift 2;;
    --ephemeral) EPHEMERAL="true"; shift;;
    --runner-version) RUNNER_VERSION="${2:-}"; shift 2;;
    -h|--help) sed -n '2,40p' "$0"; exit 0;;
    *) err "flag desconhecida: $1 (use --help)";;
  esac
done

[[ -n "$REPO" ]] || err "informe --repo owner/repo"
[[ "$REPO" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || err "--repo inválido (use owner/repo)"
command -v docker >/dev/null || err "Docker não encontrado — instale antes (curl -fsSL https://get.docker.com | sh)."
if [[ "$EPHEMERAL" == "true" ]]; then
  [[ -n "$PAT" ]] || err "--ephemeral exige --pat (para gerar tokens de registro a cada job)."
else
  [[ -n "$REG_TOKEN" || -n "$PAT" ]] || err "informe --token (registro) ou --pat."
fi

# Arquitetura do runner conforme a VM (x64 em AMD; arm64 em Ampere A1).
case "$(uname -m)" in
  x86_64|amd64) ARCH="x64";;
  aarch64|arm64) ARCH="arm64";;
  *) err "arquitetura não suportada: $(uname -m)";;
esac
info "arquitetura do runner: ${ARCH}"

# Aviso de RAM (buildar Quarkus+Angular em <4 GB é sofrível).
MEM_GB=$(( $(getconf _PHYS_PAGES) * $(getconf PAGE_SIZE) / 1024 / 1024 / 1024 ))
if (( MEM_GB < 4 )); then
  warn "esta VM tem ~${MEM_GB} GB — pouco para buildar as imagens. Use uma VM com >= 6-8 GB (ver docs/self-hosted-runner.md)."
fi

IMAGE="ebd-actions-runner:${RUNNER_VERSION}"
CONTAINER="ebd-runner"
BUILD_DIR="$(mktemp -d)"; trap 'rm -rf "$BUILD_DIR"' EXIT

# ---- Dockerfile (runner oficial + docker-cli, sem imagem de terceiros) ------
cat > "$BUILD_DIR/Dockerfile" <<DOCKER
FROM ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive
ARG RUNNER_VERSION
ARG ARCH
RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates curl jq git tar gzip sudo unzip \
      libicu74 lsb-release gnupg \
 && install -m0755 -d /etc/apt/keyrings \
 && curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc \
 && echo "deb [arch=\$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \$(. /etc/os-release && echo \$VERSION_CODENAME) stable" > /etc/apt/sources.list.d/docker.list \
 && apt-get update && apt-get install -y --no-install-recommends docker-ce-cli docker-buildx-plugin \
 && rm -rf /var/lib/apt/lists/*
RUN useradd -m -s /bin/bash runner && usermod -aG sudo runner \
 && echo "runner ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/runner
USER runner
WORKDIR /home/runner
RUN mkdir actions-runner && cd actions-runner \
 && curl -fsSL -o r.tar.gz "https://github.com/actions/runner/releases/download/v\${RUNNER_VERSION}/actions-runner-linux-\${ARCH}-\${RUNNER_VERSION}.tar.gz" \
 && tar xzf r.tar.gz && rm r.tar.gz
COPY --chown=runner:runner entrypoint.sh /home/runner/entrypoint.sh
RUN chmod +x /home/runner/entrypoint.sh
ENTRYPOINT ["/home/runner/entrypoint.sh"]
DOCKER

# ---- entrypoint: registra (token direto ou via PAT) e roda -----------------
cat > "$BUILD_DIR/entrypoint.sh" <<'ENTRY'
#!/usr/bin/env bash
set -euo pipefail
cd /home/runner/actions-runner

REPO="${REPO:?}"; RUNNER_NAME="${RUNNER_NAME:-oci-runner}"; LABELS="${LABELS:-self-hosted}"
EPHEMERAL="${EPHEMERAL:-false}"

# Token de registro: se veio um PAT, gera um token novo (necessário no modo efêmero).
if [[ -n "${ACCESS_TOKEN:-}" ]]; then
  REG_TOKEN="$(curl -fsSL -X POST \
      -H "Authorization: Bearer ${ACCESS_TOKEN}" \
      -H "Accept: application/vnd.github+json" \
      "https://api.github.com/repos/${REPO}/actions/runners/registration-token" | jq -r .token)"
fi
[[ -n "${REG_TOKEN:-}" ]] || { echo "sem token de registro"; exit 1; }

EXTRA=()
[[ "$EPHEMERAL" == "true" ]] && EXTRA+=(--ephemeral)

./config.sh --unattended --replace \
  --url "https://github.com/${REPO}" \
  --token "${REG_TOKEN}" \
  --name "${RUNNER_NAME}" \
  --labels "${LABELS}" "${EXTRA[@]}"

cleanup() { ./config.sh remove --token "${REG_TOKEN}" >/dev/null 2>&1 || true; }
trap 'cleanup; exit 0' INT TERM
./run.sh
ENTRY

info "buildando a imagem do runner (${IMAGE}) ..."
docker build -q --build-arg RUNNER_VERSION="$RUNNER_VERSION" --build-arg ARCH="$ARCH" \
  -t "$IMAGE" "$BUILD_DIR" >/dev/null
ok "imagem pronta."

# ---- sobe o container ------------------------------------------------------
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
ENV_ARGS=(-e "REPO=$REPO" -e "RUNNER_NAME=$NAME" -e "LABELS=$LABELS" -e "EPHEMERAL=$EPHEMERAL")
[[ -n "$PAT" ]]       && ENV_ARGS+=(-e "ACCESS_TOKEN=$PAT")
[[ -n "$REG_TOKEN" ]] && ENV_ARGS+=(-e "REG_TOKEN=$REG_TOKEN")

info "subindo o container '${CONTAINER}' ..."
docker run -d --name "$CONTAINER" --restart=always \
  -v /var/run/docker.sock:/var/run/docker.sock \
  "${ENV_ARGS[@]}" "$IMAGE" >/dev/null

sleep 4
echo
ok "Runner no ar. Confira em: GitHub → Settings → Actions → Runners (deve aparecer '${NAME}' online)."
cat <<TXT

Comandos úteis:
  docker logs -f ${CONTAINER}       # acompanhar o registro/execução
  docker rm -f ${CONTAINER}         # remover o runner

Próximo passo (quando o runner estiver ONLINE):
  aponte os jobs para ele nos workflows — troque 'runs-on: ubuntu-latest' por
  'runs-on: [self-hosted, linux, oci]'. Passo a passo e alertas de segurança em
  docs/self-hosted-runner.md
TXT
