# Senhas e Secrets — onde ficam e como consultar 🔐

Este guia diz **onde cada senha/segredo mora** e **como consultá-la** depois. Por segurança,
os **valores reais não ficam neste arquivo** (que é versionado) — eles vivem no seu
**gerenciador de senhas** e nos **Secrets do GitHub**.

## 1. Credenciais de acesso ao site (produção)

O login em `http://163.176.181.38` usa dois usuários criados no 1º boot:

| Usuário | Onde está a senha |
|---|---|
| `admin` (administrador) | no seu **gerenciador de senhas** (guarde ali) |
| `professor` | no seu **gerenciador de senhas** |

> As senhas foram **geradas fortes** e entregues a você no chat. Salve-as no gerenciador
> agora (veja a seção 3). Elas também estão no Secret `OCI_ENV_FILE` do GitHub, mas
> **Secrets do GitHub não podem ser lidos de volta** — só sobrescritos. Então o gerenciador
> de senhas é a sua fonte de consulta.

## 2. Secrets do GitHub (usados pelo CI/CD)

Em **GitHub → repositório → Settings → Secrets and variables → Actions**:

| Secret | O que é | Sensível? |
|---|---|---|
| `OCI_SSH_HOST` | IP público da VM (`163.176.181.38`) | não |
| `OCI_SSH_USER` | usuário SSH (`ubuntu`) | não |
| `OCI_SSH_KEY` | **chave privada** de deploy (`~/.ssh/ebd_deploy`) | **sim** |
| `OCI_ENV_FILE` | `.env` de produção (senhas do banco e dos usuários) | **sim** |
| `EBD_JWT_PRIVATE_KEY` | (opcional) chave **privada** JWT persistente — ver seção 6 | **sim** |
| `EBD_JWT_PUBLIC_KEY` | (opcional) chave **pública** JWT persistente — ver seção 6 | não |

⚠️ **Não dá para "ver" o valor de um Secret depois de salvo** — o GitHub só deixa
**substituir**. Por isso as fontes de verdade são:
- **Chave de deploy**: o arquivo `~/.ssh/ebd_deploy` no seu Mac.
- **Senhas do app/banco**: o seu **gerenciador de senhas**.

Se um secret vazar ou você perder o valor, o caminho é **rotacionar** (seção 4).

## 3. Como guardar/consultar no gerenciador de senhas

Guarde uma entrada chamada, por exemplo, **"EBD - produção"** com os campos:
`URL = http://163.176.181.38`, `admin = <senha>`, `professor = <senha>`,
e uma nota com o IP e o caminho da chave de deploy.

**macOS — App Senhas / Trousseau (Keychain):**
- App **Senhas** (Ajustes do Sistema → Senhas) → **+** → guarde URL + usuário + senha.
- Consultar depois: abra **Senhas**, busque "EBD", e clique para revelar.
- Via terminal (Keychain): guardar
  `security add-generic-password -a admin -s ebd-prod -w` (pede a senha);
  consultar `security find-generic-password -a admin -s ebd-prod -w`.

**1Password:**
- Novo item **Login** → título "EBD - produção", website, usuário, senha → Salvar.
- Consultar: busque "EBD" (app, extensão ou `op item get "EBD - produção"` no CLI).

**Bitwarden:**
- Novo item **Login** → nome "EBD - produção" → usuário/senha/URL → Salvar.
- Consultar: busca "EBD" (app/extensão ou `bw get password "EBD - produção"` no CLI).

## 4. Rotacionar (trocar) senhas quando quiser

**Senhas do app (admin/professor) e do banco** — regenere o `.env` e reaplique:
```bash
# 1) gere novas senhas e monte o .env (guarde as novas no gerenciador!)
cat > /tmp/ebd-prod.env <<EOF
POSTGRES_DB=ebd
POSTGRES_USER=ebd
POSTGRES_PASSWORD=<nova-senha-forte>
EBD_ADMIN_USERNAME=admin
EBD_ADMIN_PASSWORD=<nova-senha-admin>
EBD_PROFESSOR_USERNAME=professor
EBD_PROFESSOR_PASSWORD=<nova-senha-prof>
EOF

# 2) atualize o Secret do GitHub
gh secret set OCI_ENV_FILE < /tmp/ebd-prod.env

# 3) aplique na VM (down -v reinicia o banco e recria os usuários com as novas senhas)
scp /tmp/ebd-prod.env ubuntu@163.176.181.38:~/ebd-samambaia/.env
ssh ubuntu@163.176.181.38 'cd ~/ebd-samambaia && sudo docker compose down -v && sudo docker compose up -d'
rm /tmp/ebd-prod.env
```
> `down -v` apaga o volume do banco (recria usuários pelo seed). Faça **backup antes** se já
> houver dados reais: `docker exec ebd-postgres pg_dump -U ebd ebd > backup.sql`.

**Chave de deploy (`OCI_SSH_KEY`)** — gere outra e troque:
```bash
ssh-keygen -t ed25519 -f ~/.ssh/ebd_deploy -N "" -C "ebd-deploy-actions"
ssh-copy-id -i ~/.ssh/ebd_deploy.pub ubuntu@163.176.181.38   # instala a nova pública na VM
gh secret set OCI_SSH_KEY < ~/.ssh/ebd_deploy                # atualiza o secret
```

## 6. Chaves JWT persistentes (opt-in) — evitar deslogar todo mundo a cada deploy

Por padrão o `backend/Dockerfile` **gera um par de chaves JWT novo a cada build**, o que invalida
todos os tokens e desloga os usuários em todo deploy. Para evitar isso, o CD grava as **mesmas**
chaves (vindas de secrets) no build a cada deploy — se os secrets existirem. Sem os secrets, nada
muda (comportamento antigo).

**Ativar (uma vez):**
```bash
# 1) gerar o par no mesmo formato que o Dockerfile usa (RSA 2048)
openssl genrsa -out /tmp/privateKey.pem 2048
openssl rsa -in /tmp/privateKey.pem -pubout -out /tmp/publicKey.pem

# 2) cadastrar como secrets do GitHub
gh secret set EBD_JWT_PRIVATE_KEY < /tmp/privateKey.pem
gh secret set EBD_JWT_PUBLIC_KEY  < /tmp/publicKey.pem

# 3) guardar um backup local (gitignored) e apagar os temporários
mkdir -p .secrets-local && cp /tmp/privateKey.pem /tmp/publicKey.pem .secrets-local/
rm /tmp/privateKey.pem /tmp/publicKey.pem
```

- No **primeiro** deploy após adicionar os secrets, os tokens atuais (assinados com a chave antiga
  embutida) deixam de valer — é **um único** logout geral. A partir daí, os tokens sobrevivem aos deploys.
- O CD (`.github/workflows/cd.yml`, passo *"Chaves JWT persistentes"*) grava os `.pem` em
  `backend/src/main/resources/` na VM **após o rsync** e **antes** do build; o Dockerfile detecta que
  já existem e não gera novas.
- Rotacionar as chaves = repetir os passos acima com um par novo (causa um logout geral).

## 5. Regra de ouro

- **Nunca** comite senhas/chaves no Git. As chaves JWT (`*.pem`) e o `scripts/.oci-launch.env`
  já estão no `.gitignore`.
- Fonte de verdade das senhas = **gerenciador de senhas** (não os Secrets, que são "cegos").
