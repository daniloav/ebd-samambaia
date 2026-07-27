# Integração externa da tesouraria (view somente-leitura)

Este runbook explica como dar a um **sistema de terceiros** (o software do tesoureiro)
acesso **somente-leitura** às requisições da tesouraria, via uma **view** no PostgreSQL,
com um **usuário/senha dedicado** e o **IP dele liberado** no firewall.

> **Papéis nesta operação**
> - **Você (operador/Danilo)** executa tudo que envolve **senha, banco e firewall** — são
>   ações sensíveis (credenciais e infra). O passo a passo abaixo é para você rodar.
> - **O Claude** entrega a *view* (migration `V17`) e este manual. Ele **não gera senha**,
>   **não abre porta/firewall** e **não recebe a credencial** — isso é sempre com você.

---

## 1. O que é exposto (e o que NÃO é)

A migration [`V17__view_requisicoes_integracao.sql`](../backend/src/main/resources/db/migration/V17__view_requisicoes_integracao.sql)
cria a view **`vw_requisicoes_integracao`**, com uma linha por requisição:

| Coluna | Conteúdo |
|---|---|
| `requisicao_id`, `numero` | id interno e nº único (`REQ-2026-0001`) |
| `status` | `ABERTA` / `APROVADA` / `NEGADA` / `FINALIZADA` / `CANCELADA` |
| `ministerio`, `nome_evento`, `destinacao`, `motivo` | dados do pedido |
| `valor_solicitado`, `valor_aprovado`, `valor_gasto` | valores |
| `data_necessidade`, `criado_em`, `avaliado_em`, `finalizado_em` | datas |
| `solicitante`, `solicitante_email` | quem pediu (nome do aluno se houver, senão o login) |
| `avaliado_por`, `parecer_tesoureiro`, `observacao_final` | avaliação e prestação de contas |
| `qtd_anexos`, `possui_nota_fiscal` | quantas notas foram anexadas / se já tem nota |

**Nunca é exposto:** `senha_hash`, o **conteúdo binário** das notas fiscais (`bytea`),
nem qualquer outra tabela. A view roda com a permissão do **dono** (`ebd`), então o usuário
read-only enxerga **só a view** — não consegue ler as tabelas por baixo.

---

## 2. ⚠️ Antes de tudo: como o banco está hoje

Hoje o Postgres da `ebd-db` (`136.248.80.0` / privado `10.0.1.54`):

- faz **bind só no IP privado** (`EBD_DB_BIND_IP=10.0.1.54`) — **não escuta no IP público**;
- só aceita a porta **5432 de `10.0.1.45/32`** (a VM do app), em duas camadas: **Security List**
  da subnet + **iptables** no host.

Ou seja: **liberar o IP do tesoureiro no allowlist, sozinho, não basta** — enquanto o bind for
só o privado, o pacote dele nem chega ao Postgres. Há dois caminhos:

| Caminho | Abre 5432 pra internet? | Esforço | Recomendação |
|---|---|---|---|
| **A) Túnel SSH** (seção 5) | **Não** | Baixo | ✅ **Recomendado** |
| **B) Exposição direta + allowlist de IP** (seção 6) | Sim (só p/ o IP dele) | Médio | ⚠️ Só se o sistema dele não fizer túnel |

Faça **os passos 3 e 4 sempre**; depois escolha **A ou B**.

---

## 3. Aplicar a view

A view entra pelo Flyway no próximo deploy (é a `V17`). Para aplicar **agora**, sem esperar deploy,
rode na `ebd-db` (em `~/ebd-db`):

```bash
docker compose exec -T db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < V17__view_requisicoes_integracao.sql
```

> Copie o arquivo `V17__...sql` para a VM antes, ou cole o conteúdo. É `CREATE OR REPLACE VIEW`
> (idempotente): pode rodar de novo sem problema, e o Flyway não vai reclamar no deploy seguinte.

Confira:

```bash
docker compose exec db psql -U ebd -d ebd -c "SELECT numero, status, valor_solicitado FROM vw_requisicoes_integracao LIMIT 3;"
```

---

## 4. Gerar a senha e criar o usuário read-only

### 4.1. Gerar uma senha forte (você roda; o Claude não gera senha)

No **seu** terminal (não precisa ser na VM):

```bash
openssl rand -base64 24
```

Guarde o resultado no seu **gerenciador de senhas**. Essa é a senha do usuário `tesouraria_ro`.
**Nunca** cole essa senha em arquivo versionado, e-mail ou chat.

### 4.2. Criar o usuário com permissão mínima

Na `ebd-db`, abra o psql como dono do banco:

```bash
docker compose exec db psql -U ebd -d ebd
```

E rode (troque `COLE_A_SENHA_AQUI` pela senha gerada acima):

```sql
-- usuário só-login, sem privilégio nenhum por padrão
CREATE ROLE tesouraria_ro WITH LOGIN PASSWORD 'COLE_A_SENHA_AQUI' CONNECTION LIMIT 5;

-- least privilege: só conectar, enxergar o schema e ler A VIEW
REVOKE ALL      ON DATABASE ebd     FROM tesouraria_ro;
GRANT  CONNECT  ON DATABASE ebd     TO   tesouraria_ro;
GRANT  USAGE    ON SCHEMA   public  TO   tesouraria_ro;
REVOKE CREATE   ON SCHEMA   public  FROM tesouraria_ro;
GRANT  SELECT   ON vw_requisicoes_integracao TO tesouraria_ro;
```

Teste que ele **lê a view** mas **não** as tabelas:

```sql
SET ROLE tesouraria_ro;
SELECT count(*) FROM vw_requisicoes_integracao;        -- deve funcionar
SELECT count(*) FROM requisicao_tesouraria;            -- deve dar "permission denied"
RESET ROLE;
```

> Para **rotacionar** a senha depois: `ALTER ROLE tesouraria_ro PASSWORD 'nova_senha';`
> Para **revogar** o acesso: `ALTER ROLE tesouraria_ro NOLOGIN;` (ou `DROP ROLE tesouraria_ro;`).

---

## 5. Caminho A — Túnel SSH (recomendado, não abre 5432)

O sistema do tesoureiro conecta em `localhost` na máquina dele, e um túnel SSH encaminha
o tráfego (criptografado) até o Postgres privado. **Nada é aberto na internet.**

1. Crie na `ebd-db` uma **chave SSH dedicada só para túnel** para o tesoureiro (sem shell):
   em `~/.ssh/authorized_keys`, prefixe a chave pública dele com restrições, ex.:

   ```
   no-pty,no-agent-forwarding,no-X11-forwarding,permitopen="10.0.1.54:5432" ssh-ed25519 AAAA...chave-do-tesoureiro
   ```

   (Só permite encaminhar para o Postgres; não dá terminal.)

2. Na máquina **dele**, o túnel:

   ```bash
   ssh -N -L 5433:10.0.1.54:5432 usuario_tunel@136.248.80.0
   ```

3. O sistema dele então aponta para **`localhost:5433`**, banco `ebd`, usuário `tesouraria_ro`.

Assim você só precisa que a porta **22** da `ebd-db` aceite o IP dele (Security List + iptables),
e o 5432 continua fechado pro mundo.

---

## 6. Caminho B — Exposição direta + allowlist do IP do tesoureiro

Use só se o sistema dele **não** suportar túnel. Aqui o 5432 passa a aceitar conexão do IP
dele. **Você executa cada passo** (são mudanças de infra/VM).

Anote o **IP público fixo** do tesoureiro: `TESOUREIRO_IP` (ex.: `200.100.50.10`). Ele precisa
ser **fixo**; se for dinâmico, prefira o Caminho A.

### 6.1. Fazer o Postgres escutar além do IP privado

No `.env` da `ebd-db`, troque o bind para todas as interfaces (o firewall é que restringe quem chega):

```dotenv
EBD_DB_BIND_IP=0.0.0.0
```

Recrie o container:

```bash
docker compose -f docker-compose.db.yml --env-file .env up -d
```

> Bind em `0.0.0.0` **por si só não expõe** nada: quem filtra é a Security List + iptables abaixo.
> Só avance depois de ter as regras de firewall restritas ao `/32` prontas.

### 6.2. Liberar o IP na Security List (OCI Console)

Console OCI → **Networking → Virtual Cloud Networks →** sua VCN **→** a **subnet** da `ebd-db`
**→ Security List → Add Ingress Rules**:

- **Source Type**: CIDR
- **Source CIDR**: `TESOUREIRO_IP/32`
- **IP Protocol**: TCP
- **Destination Port Range**: `5432`
- Description: `tesouraria-ro leitura externa`

Mantenha a regra existente (`10.0.1.45/32 → 5432`) — o app continua precisando dela.

### 6.3. Liberar o IP no iptables da `ebd-db`

Na VM `ebd-db`:

```bash
# aceita o IP do tesoureiro na 5432 (antes de qualquer DROP genérico)
sudo iptables -I INPUT -p tcp -s TESOUREIRO_IP/32 --dport 5432 -j ACCEPT
# confira a ordem das regras
sudo iptables -L INPUT -n --line-numbers | grep 5432
# persistir (Ubuntu)
sudo netfilter-persistent save     # ou: sudo sh -c 'iptables-save > /etc/iptables/rules.v4'
```

### 6.4. (Recomendado) Exigir SSL

A imagem `postgres:16-alpine` não vem com SSL ligado. Para trânsito na internet, o ideal é
habilitar TLS no Postgres (certificado + `ssl=on`) e o cliente conectar com `sslmode=require`.
Se isso for muito, **prefira o Caminho A** (o SSH já criptografa tudo).

---

## 7. String de conexão do tesoureiro

| Campo | Caminho A (túnel) | Caminho B (direto) |
|---|---|---|
| Host | `localhost` | `136.248.80.0` |
| Porta | `5433` | `5432` |
| Banco | `ebd` | `ebd` |
| Usuário | `tesouraria_ro` | `tesouraria_ro` |
| Senha | *(a gerada na 4.1)* | *(a gerada na 4.1)* |
| SSL | (o túnel já cifra) | `sslmode=require` (se ativou SSL) |

Exemplo de teste (na máquina dele, com o cliente `psql`):

```bash
# Caminho A
psql "host=localhost port=5433 dbname=ebd user=tesouraria_ro"
# Caminho B
psql "host=136.248.80.0 port=5432 dbname=ebd user=tesouraria_ro sslmode=require"
```

```sql
SELECT numero, status, valor_aprovado, solicitante FROM vw_requisicoes_integracao ORDER BY criado_em DESC;
```

---

## 8. Entregar a senha com segurança

- Envie a senha por um **canal seguro**: um item compartilhado do **gerenciador de senhas**,
  ou um link que expira. **Nunca** por e-mail/WhatsApp em texto puro, nem no mesmo canal do host/usuário.
- Combine com o tesoureiro a **rotação periódica** (ex.: a cada 6 meses) e a **revogação imediata**
  se o sistema dele mudar ou vazar.

## 9. Manutenção rápida

| Ação | Comando (no psql da `ebd-db`, como `ebd`) |
|---|---|
| Rotacionar senha | `ALTER ROLE tesouraria_ro PASSWORD 'nova';` |
| Suspender acesso | `ALTER ROLE tesouraria_ro NOLOGIN;` |
| Reativar | `ALTER ROLE tesouraria_ro LOGIN;` |
| Remover de vez | `DROP ROLE tesouraria_ro;` + remover regras de firewall (6.2/6.3) |
| Ver quem está conectado | `SELECT usename, client_addr, state FROM pg_stat_activity WHERE usename='tesouraria_ro';` |

> Se ampliar o que a integração enxerga no futuro, **edite a view** numa **nova migration**
> (`V18__...`) — não altere a `V17` já aplicada (regra de ouro do Flyway).
