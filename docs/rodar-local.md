# Rodar o projeto localmente (sem o Claude) 🖥️

Guia passo a passo para subir a aplicação na sua máquina. São **3 serviços**: banco
(PostgreSQL), backend (Quarkus) e frontend (Angular).

> ⚠️ **Docker não funciona neste Mac** (o daemon trava). Por isso, no dev local usamos o
> **PostgreSQL nativo via Homebrew** em vez do `docker-compose.dev.yml`. Na VM de produção
> o Docker é usado normalmente.

---

## ⚡ Jeito fácil: um comando só

Depois de ter **Java, Maven, Node e Homebrew** instalados, é só:

```bash
cd ~/claude-trabalho
./scripts/dev-up.sh      # sobe banco + backend + frontend (idempotente)
./scripts/dev-down.sh    # para backend e frontend
```

O `dev-up.sh` instala o Postgres se faltar, cria o banco `ebd`, gera as chaves JWT,
instala as dependências do front, sobe tudo e espera ficar pronto — aí abre
**http://localhost:4200** (login `admin` / `admin123`).

Logs: `tail -f /tmp/ebd-backend.log` e `tail -f /tmp/ebd-frontend.log`.

> Prefere entender/fazer manualmente? Continue lendo as partes abaixo.

---

## Parte 1 — Configuração (só na PRIMEIRA vez)

### 1.1 Pré-requisitos (já instalados na sua máquina)
- **Java 17+** (`java -version`)
- **Maven** (`mvn -version`)
- **Node 18.13+** e **npm** (`node -v`)
- **Homebrew** (`brew -v`)

### 1.2 Instalar e preparar o PostgreSQL
```bash
brew install postgresql@16
brew services start postgresql@16        # sobe agora e a cada login

# criar o usuário e o banco que a aplicação espera (usuário/senha/banco = ebd)
PSQL=/opt/homebrew/opt/postgresql@16/bin/psql
"$PSQL" -d postgres -c "CREATE ROLE ebd WITH LOGIN PASSWORD 'ebd';"
"$PSQL" -d postgres -c "CREATE DATABASE ebd OWNER ebd;"
```
> As migrations (criação das tabelas) rodam sozinhas quando o backend sobe (Flyway).

### 1.3 Gerar as chaves JWT do backend
```bash
cd ~/claude-trabalho
./scripts/gen-jwt-keys.sh
```

### 1.4 Instalar as dependências do frontend
```bash
cd ~/claude-trabalho/frontend
npm install
```

---

## Parte 2 — Rodar (TODA vez)

Abra **3 terminais** (ou 3 abas). O Postgres já sobe sozinho no login; se precisar garantir:

**Terminal 1 — Banco** (normalmente já está rodando):
```bash
brew services start postgresql@16
```

**Terminal 2 — Backend** (porta 8080):
```bash
cd ~/claude-trabalho/backend
mvn quarkus:dev
```
Espere aparecer algo como `Listening on: http://localhost:8080`.
- Se ele perguntar sobre "build analytics", pode ignorar (não responder tudo bem).
- Swagger da API: http://localhost:8080/q/swagger-ui

**Terminal 3 — Frontend** (porta 4200):
```bash
cd ~/claude-trabalho/frontend
npm start
```
Espere `Local: http://localhost:4200/`.

### Acessar
Abra **http://localhost:4200** e entre com:
- **admin / admin123** (administrador) ou
- **professor / prof123** (professor)

---

## Parte 3 — Parar

- **Backend / Frontend**: `Ctrl + C` em cada terminal.
- **Banco** (opcional — pode deixar rodando):
  ```bash
  brew services stop postgresql@16
  ```

---

## Atalhos úteis

| Ação | Comando |
|---|---|
| Ver se o Postgres está no ar | `/opt/homebrew/opt/postgresql@16/bin/pg_isready -h localhost` |
| Abrir o banco no psql | `/opt/homebrew/opt/postgresql@16/bin/psql -d ebd` |
| Validar backend sem subir (build) | `cd backend && mvn -q -B package -DskipTests` |
| Validar frontend (build) | `cd frontend && npx ng build` |
| Recriar as chaves JWT | `./scripts/gen-jwt-keys.sh --force` |

---

## Problemas comuns

| Sintoma | Causa | Solução |
|---|---|---|
| Backend: `Connection to localhost:5432 refused` | Postgres não está rodando | `brew services start postgresql@16` |
| Backend: `password authentication failed` | role `ebd` não existe/sem senha | rode de novo o passo **1.2** |
| Backend não acha as chaves JWT | chaves não geradas | `./scripts/gen-jwt-keys.sh` |
| Frontend: `ng: command not found` | usar `npm start` (não `ng`) | `cd frontend && npm start` |
| Porta 8080/4200 "já em uso" | um processo antigo ficou preso | `lsof -nP -iTCP:8080 -sTCP:LISTEN` e `kill <PID>` |
| Login não passa do "Entrando..." | backend caiu ou sem banco | ver o Terminal 2 (erros do Quarkus) |

---

## Resumo rápido (cola)

```bash
# uma vez:
brew install postgresql@16 && brew services start postgresql@16
PSQL=/opt/homebrew/opt/postgresql@16/bin/psql
"$PSQL" -d postgres -c "CREATE ROLE ebd LOGIN PASSWORD 'ebd';"
"$PSQL" -d postgres -c "CREATE DATABASE ebd OWNER ebd;"
cd ~/claude-trabalho && ./scripts/gen-jwt-keys.sh && (cd frontend && npm install)

# toda vez (3 terminais):
brew services start postgresql@16          # 1) banco
cd ~/claude-trabalho/backend && mvn quarkus:dev    # 2) backend :8080
cd ~/claude-trabalho/frontend && npm start         # 3) frontend :4200
# abrir http://localhost:4200  (admin / admin123)
```

## Testes automatizados (backend)

Os `@QuarkusTest` rodam contra um **Postgres real** (banco separado `ebd_test`), não o Dev Services
(este Mac não sobe Docker). Uma vez, crie o banco:
```bash
createdb -h localhost -O ebd ebd_test    # dono = role ebd (Flyway precisa criar o schema)
```
Depois:
```bash
cd backend && mvn test        # ou: mvn verify
```
O Flyway aplica as migrations no `ebd_test` no boot do teste; cada teste usa `@TestTransaction`
(rollback) para não sujar o banco. Cobrem: login/JWT + proteção de rota, chamada (upsert),
notas (validação + gravação), relatório (agregação) e campanha (e-mail via `MockMailbox`).
No CI o mesmo `mvn verify` roda contra um serviço Postgres (`ebd_test`).
