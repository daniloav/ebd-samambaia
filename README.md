# EBD Adultos — ICEV Samambaia 📖

Hotsite para gestão da **Escola Bíblica Dominical (classe de adultos)** da Igreja Cristã Evangélica em Samambaia.

Dois módulos no MVP:

- **Chamada** — controle de presença e itens por aluno (presente, trouxe Bíblia, trouxe revista, estudou a lição), CRUD de alunos e relatório de presenças.
- **Desafios** — rankings de premiação (menos faltou, mais trouxe Bíblia/revista, mais estudou a lição, melhores notas), com o submódulo **Provas** (CRUD de provas + lançamento de notas).

## 🧱 Arquitetura

| Camada | Tecnologia |
|---|---|
| Backend | Java 17 · **Quarkus 3** · Hibernate ORM Panache · Flyway · JWT (roles) |
| Frontend | **Angular 17** (standalone) · SCSS |
| Banco | **PostgreSQL 16** |
| Infra | Docker · Docker Compose · nginx |

```
claude-trabalho/
├── backend/     API Quarkus  (br.com.icev.ebd)
├── frontend/    App Angular
├── docker-compose.yml       stack completo (prod / VM)
├── docker-compose.dev.yml   só o Postgres (dev)
└── docs/deploy-oracle.md    passo a passo na Oracle Cloud
```

## 🔐 Perfis de acesso

| Perfil | Pode |
|---|---|
| **ADMIN** | tudo: CRUD de alunos e provas, excluir aulas, chamada, notas, relatórios, rankings |
| **PROFESSOR** | fazer chamada, criar aulas, lançar notas, ver relatórios e rankings, consultar alunos |

Usuários criados automaticamente no **primeiro boot** (troque as senhas!):

| Usuário | Senha | Perfil |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `professor` | `prof123` | PROFESSOR |

## 🚀 Rodando em desenvolvimento

Pré-requisitos: **JDK 17+**, **Maven**, **Node 18.13+**, **Docker**.

**1. Suba o banco**
```bash
docker compose -f docker-compose.dev.yml up -d
```

**2. Backend** (porta 8080):
```bash
./scripts/gen-jwt-keys.sh    # gera as chaves JWT (só na 1ª vez)
cd backend
mvn quarkus:dev
```
- API: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/q/swagger-ui`

**3. Frontend** (porta 4200) — em `frontend/`:
```bash
cd frontend
npm install
npm start
```
Acesse `http://localhost:4200` e entre com `admin / admin123`.

## 📦 Rodando o stack completo (como em produção)

```bash
docker compose up -d --build
```
Sobe Postgres + backend + frontend. Acesse `http://localhost` (porta 80).
O nginx serve o Angular e faz proxy de `/api` para o backend.

Copie `.env.example` para `.env` para customizar credenciais:
```bash
cp .env.example .env
```

## 🗃️ Principais endpoints da API

| Método | Rota | Perfil |
|---|---|---|
| POST | `/api/auth/login` | público |
| GET/POST/PUT/DELETE | `/api/alunos` | leitura: ambos · escrita: ADMIN |
| GET/POST/PUT/DELETE | `/api/aulas` | ambos (excluir: ADMIN) |
| GET/PUT | `/api/aulas/{id}/chamada` | ambos |
| GET | `/api/relatorios/presencas?inicio&fim` | ambos |
| GET/POST/PUT/DELETE | `/api/provas` | leitura: ambos · escrita: ADMIN |
| GET/PUT | `/api/provas/{id}/notas` | ambos |
| GET | `/api/desafios/rankings` | ambos |

## 🔒 Segredos e chaves

Nada sensível é versionado. Antes de rodar:

- **Chaves JWT** (`backend/src/main/resources/privateKey.pem` / `publicKey.pem`):
  geradas por `./scripts/gen-jwt-keys.sh`. No Docker, o `backend/Dockerfile`
  gera automaticamente se não existirem.
- **Config de lançamento OCI** (`scripts/.oci-launch.env`): copie de
  `scripts/.oci-launch.env.example` e preencha (usado só pelos scripts de deploy).

Ambos estão no `.gitignore`.

## ☁️ Deploy na Oracle Cloud

Veja o guia completo em [`docs/deploy-oracle.md`](docs/deploy-oracle.md).

## 📤 Subindo para o GitHub

```bash
git add .
git commit -m "MVP EBD Adultos"
git branch -M main
git remote add origin git@github.com:SEU_USUARIO/ebd-samambaia.git
git push -u origin main
```
