# Runbook — pós-provisionamento da VM (deploy no ar)

> ℹ️ **Nota:** este guia descreve o **provisionamento inicial de uma VM** e o deploy antigo
> (build na VM). A **infra atual** são **2 VMs (app + banco) + imagens no GHCR** — veja
> [`topologia.md`](topologia.md). Use este doc só para criar/reprovisionar uma VM.


Siga na ordem quando o `oci-a1-retry.sh` mostrar `✅ SUCESSO!`. Substitua `SEU_IP` pelo IP público da VM.

## 0. Descobrir o IP público da VM

Se o log do retry não mostrou, pegue via CLI:
```bash
TEN=$(grep -E '^tenancy=' ~/.oci/config | head -1 | cut -d= -f2)
INST=$(oci compute instance list --compartment-id "$TEN" --display-name ebd-server \
  --lifecycle-state RUNNING --query 'data[0].id' --raw-output)
oci compute instance list-vnics --instance-id "$INST" \
  --query 'data[0]."public-ip"' --raw-output
```
Guarde o IP (ex.: `140.238.10.20`).

## 1. Preparar a VM (Docker + firewall)

```bash
ssh ubuntu@SEU_IP          # 1º acesso (aceite a fingerprint)
# já dentro da VM:
curl -fsSL https://raw.githubusercontent.com/daniloav/ebd-samambaia/main/scripts/oci-bootstrap.sh -o bootstrap.sh
# (repo privado? então copie o arquivo do seu Mac:)
#   scp scripts/oci-bootstrap.sh ubuntu@SEU_IP:~/bootstrap.sh
bash bootstrap.sh
exit                       # sair e reconectar para o grupo docker valer
```

## 2. Gerar uma chave de deploy dedicada (no seu Mac)

Não reutilize sua chave pessoal — crie uma só para o CI:
```bash
ssh-keygen -t ed25519 -f ~/.ssh/ebd_deploy -C "deploy-actions" -N ""
```

Instalar a **pública** na VM (usa seu acesso atual):
```bash
ssh-copy-id -i ~/.ssh/ebd_deploy.pub ubuntu@SEU_IP
# alternativa manual:
#   cat ~/.ssh/ebd_deploy.pub | ssh ubuntu@SEU_IP 'cat >> ~/.ssh/authorized_keys'
```

Testar a chave de deploy:
```bash
ssh -i ~/.ssh/ebd_deploy ubuntu@SEU_IP 'echo ok, deploy key funciona'
```

## 3. Atualizar os secrets no GitHub (via `gh`, já autenticado)

> Os 4 secrets **já existem** com placeholder `CHANGEME` (o CD fica em mock até você trocar
> `OCI_SSH_HOST` e `OCI_SSH_KEY`). `gh secret set` **sobrescreve** o valor existente.

Rode no diretório do projeto (`~/claude-trabalho`):
```bash
cd ~/claude-trabalho

gh secret set OCI_SSH_HOST --body "SEU_IP"
gh secret set OCI_SSH_USER --body "ubuntu"
gh secret set OCI_SSH_KEY  < ~/.ssh/ebd_deploy          # chave PRIVADA (do arquivo)

# (opcional, recomendado) .env de produção com senhas fortes:
cat > /tmp/ebd.env <<'EOF'
POSTGRES_DB=ebd
POSTGRES_USER=ebd
POSTGRES_PASSWORD=TROQUE_por_senha_forte
EBD_ADMIN_USERNAME=admin
EBD_ADMIN_PASSWORD=TROQUE_admin
EBD_PROFESSOR_USERNAME=professor
EBD_PROFESSOR_PASSWORD=TROQUE_prof
EOF
gh secret set OCI_ENV_FILE < /tmp/ebd.env
rm /tmp/ebd.env

# conferir:
gh secret list
```

## 4. Disparar o deploy

Opção A — manual (imediato):
```bash
gh workflow run "CD · Deploy OCI"
gh run watch "$(gh run list --workflow='CD · Deploy OCI' --limit 1 --json databaseId --jq '.[0].databaseId')" --exit-status
```
Opção B — automática: qualquer `git push` na `main` roda CI → CD.

> O CD faz: rsync do código → `docker compose up -d --build` na VM → healthcheck.
> A **primeira** build baixa dependências (Maven + npm) e pode levar alguns minutos.

## 5. Verificar no ar

```bash
curl -I http://SEU_IP           # deve responder 200 (Angular)
curl -s http://SEU_IP/q/health  # {"status":"UP",...}
```
No navegador: `http://SEU_IP` → login **admin / (senha que você definiu)**.

## 6. Pós-deploy (importante)

- **Troque as senhas padrão** se não usou o `OCI_ENV_FILE`.
- (Opcional) HTTPS com domínio + Caddy/certbot — ver [`deploy-oracle.md`](deploy-oracle.md).
- (Opcional) Budget/alerta de custo na OCI.

## Troubleshooting rápido

| Sintoma | Causa provável | Ação |
|---|---|---|
| CD falha no "Configurar SSH" | secret `OCI_SSH_KEY` errado | recadastre a chave **privada** `~/.ssh/ebd_deploy` |
| `curl` não conecta na 80 | firewall da VM ou Security List | rode o bootstrap; confira ingress 80 na Security List |
| Healthcheck falha mas containers de pé | build ainda subindo | aguarde e rode `docker compose ps` / `logs` na VM |
| `permission denied (publickey)` no rsync | chave pública não está na VM | refaça o passo 2 (`ssh-copy-id`) |
