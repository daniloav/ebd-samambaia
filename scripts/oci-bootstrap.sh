#!/usr/bin/env bash
# ============================================================
# Prepara uma VM Ubuntu (Oracle Cloud) para rodar o projeto.
# Rode NA VM, uma única vez, após o primeiro acesso SSH:
#   ssh ubuntu@SEU_IP
#   curl -fsSL https://raw.githubusercontent.com/daniloav/ebd-samambaia/main/scripts/oci-bootstrap.sh | bash
#   (ou copie o arquivo e rode: bash oci-bootstrap.sh)
# ============================================================
set -euo pipefail

echo "▶ Atualizando pacotes..."
sudo apt-get update -y

echo "▶ Instalando git, openssl, curl..."
sudo apt-get install -y git openssl ca-certificates curl rsync

echo "▶ Instalando Docker + Compose plugin (script oficial)..."
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
fi
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER" || true

echo "▶ Liberando portas 80 e 443 no firewall da VM..."
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT || true
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT || true
if ! command -v netfilter-persistent >/dev/null 2>&1; then
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y iptables-persistent || true
fi
sudo netfilter-persistent save 2>/dev/null || true

echo
echo "✅ VM preparada!"
echo "   Saia e entre de novo no SSH para o grupo 'docker' passar a valer:"
echo "     exit && ssh $USER@<IP>"
echo
echo "Deploy — escolha um caminho:"
echo "  A) AUTOMÁTICO (recomendado): cadastre os secrets no GitHub"
echo "     (OCI_SSH_HOST / OCI_SSH_USER / OCI_SSH_KEY) e o workflow CD faz o deploy."
echo
echo "  B) MANUAL nesta VM:"
echo "     git clone https://github.com/daniloav/ebd-samambaia.git ~/ebd-samambaia"
echo "     cd ~/ebd-samambaia"
echo "     cp .env.example .env && nano .env    # defina senhas fortes"
echo "     docker compose up -d --build          # chaves JWT são geradas no build"
echo "     # acesse http://<IP>"
echo
echo "  Obs.: o repo é privado — para o clone manual (B), gere um token/deploy key"
echo "        no GitHub, ou use o caminho automático (A), que envia o código via rsync."
