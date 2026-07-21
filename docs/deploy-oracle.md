# Deploy na Oracle Cloud (VM) 🚀

> ℹ️ **Nota:** este guia descreve o **provisionamento inicial de uma VM** e o deploy antigo
> (build na VM). A **infra atual** são **2 VMs (app + banco) + imagens no GHCR** — veja
> [`topologia.md`](topologia.md). Use este doc só para criar/reprovisionar uma VM.


Guia para hospedar o hotsite em uma VM da Oracle Cloud Infrastructure (OCI), usando Docker Compose.

## 1. Criar a instância (VM)

1. No console da OCI: **Compute → Instances → Create Instance**.
2. **Image & shape**: Ubuntu 22.04. Para o *Always Free*, use `VM.Standard.A1.Flex` (ARM/Ampere, até 4 OCPU / 24 GB) ou `VM.Standard.E2.1.Micro` (x86).
   > Obs.: as imagens Docker deste projeto rodam tanto em ARM quanto x86.
3. Adicione sua **chave SSH pública**.
4. Anote o **IP público** da instância.

## 2. Liberar as portas

**a) Security List (firewall da OCI)** — em **Networking → VCN → Subnet → Security List**, adicione *Ingress Rules*:

| Origem | Protocolo | Porta |
|---|---|---|
| 0.0.0.0/0 | TCP | 80 (HTTP) |
| 0.0.0.0/0 | TCP | 443 (HTTPS, se for usar TLS) |

**b) Firewall interno da VM** (Ubuntu usa iptables/netfilter na OCI):
```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 3. Instalar Docker

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-plugin git
sudo systemctl enable --now docker
sudo usermod -aG docker $USER   # relogue depois deste comando
```

## 4. Clonar e subir

```bash
git clone https://github.com/SEU_USUARIO/ebd-samambaia.git
cd ebd-samambaia

cp .env.example .env
nano .env    # defina senhas fortes para POSTGRES_PASSWORD e os usuários

docker compose up -d --build
```

Acesse `http://SEU_IP_PUBLICO`. Faça login e **troque as senhas padrão**.

## 5. Comandos úteis

```bash
docker compose ps            # status dos serviços
docker compose logs -f backend
docker compose down          # parar (mantém o volume/banco)
docker compose up -d --build # atualizar após git pull
```

## 6. (Recomendado) HTTPS com domínio próprio

Se tiver um domínio apontando para o IP:

1. Aponte um registro **A** do seu domínio para o IP público da VM.
2. Use o [nginx-proxy + acme-companion] ou o **Caddy** como reverse proxy à frente,
   ou instale o **certbot** e monte os certificados no container do frontend.
3. Alternativa simples: colocar um **Caddy** na frente fazendo TLS automático e
   encaminhando para o serviço `frontend`.

## 7. Backup do banco

```bash
# backup
docker exec ebd-postgres pg_dump -U ebd ebd > backup_$(date +%F).sql
# restore
cat backup.sql | docker exec -i ebd-postgres psql -U ebd -d ebd
```

## Notas de segurança

- Troque **todas** as senhas padrão (`.env`) antes de expor na internet.
- As **chaves JWT** não são versionadas. No deploy com Docker, o `backend/Dockerfile`
  **gera um par novo automaticamente** durante o build — não precisa fazer nada.
  (Se rodar o backend fora do Docker, gere com `./scripts/gen-jwt-keys.sh`.)
- Cada rebuild da imagem gera novas chaves (os usuários só precisam logar de novo).
  Se quiser chaves fixas, gere uma vez e monte via volume apontando para
  `privateKey.pem`/`publicKey.pem`.
