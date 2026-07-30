# Testes E2E + Acessibilidade (Playwright + axe)

Camada de **testes de ponta-a-ponta** (regressão dos fluxos reais no navegador) e
**acessibilidade** (auditoria WCAG 2.1 A/AA com [axe-core]), rodando pelo
[Playwright]. Complementam os testes de unidade/integração do backend
(`mvn verify`, ~51 testes) cobrindo o que só aparece no navegador: login, guards
por papel, renderização das telas e conformidade de acessibilidade.

## O que é coberto

**Regressão** (`frontend/e2e/regressao.spec.ts`):
- Autenticação: credenciais inválidas, login de admin/professor/aluno, destino de
  cada papel (`/painel` vs `/conta` do 1º acesso do aluno), rota interna sem sessão → `/login`.
- Guards por papel: aluno não entra na gestão; professor não entra em telas de admin.
- Fumaça de navegação: cada tela de gestão renderiza, mantém a URL (não é expulsa
  por guard), mostra a casca (menu) e **não** dispara erro de runtime no console.

**Acessibilidade** (`frontend/e2e/acessibilidade.spec.ts`):
- axe-core com as regras `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`.
- Telas públicas (login, recuperar) + telas de gestão (admin) + `/conta` do aluno.
- **Bloqueia o build** em violação `serious`/`critical`; as demais (`minor`/`moderate`)
  viram relatório anexado ao teste (não bloqueiam). Regra em `e2e/util/a11y.ts`.

## Pré-requisitos (a stack precisa estar no ar)

O Playwright sobe **só o frontend** (`ng serve` em `:4200`). O **backend** (`:8080`)
e o **Postgres** precisam estar rodando por fora, com o banco **semeado** (o
`DataInitializer` cria `admin`/`professor` e os alunos de exemplo no 1º boot de um
banco vazio). As contas usadas nos testes estão em `e2e/util/contas.ts`.

## Rodar localmente

```bash
# 1) Postgres + backend em dev (o perfil %dev já libera CORS para o :4200)
docker compose -f docker-compose.dev.yml up -d        # ou Postgres nativo
cd backend && mvn quarkus:dev

# 2) Em outro terminal: instalar navegador (1ª vez) e rodar os testes
cd frontend
npm ci
npx playwright install --with-deps chromium
npm run e2e            # regressão + acessibilidade (headless)
npm run e2e:ui         # modo interativo (debug visual)
npm run e2e:a11y       # só a acessibilidade
npm run e2e:report     # abre o último relatório HTML
```

> O Playwright reaproveita um `ng serve` já aberto (`reuseExistingServer`) fora do CI;
> se nada estiver na `:4200`, ele mesmo sobe o dev server.
>
> **Node:** o Playwright está fixado em `1.49.1` para continuar rodando no **Node 18**
> (ambiente local do projeto). O CI usa Node 20.

## No GitHub Actions

Job **`e2e`** em [`.github/workflows/ci.yml`](../.github/workflows/ci.yml): sobe
Postgres (service), empacota e sobe o backend (jar) com CORS liberado para o `:4200`
e e-mail desligado, instala o Chromium do Playwright e roda a suíte. O relatório
HTML (com os anexos JSON do axe) é publicado como **artifact `playwright-report`**
(retido 14 dias) mesmo quando o job falha.

## Como evoluir

- **Nova tela** → adicione a rota nas listas `TELAS_GESTAO` (regressão e/ou acessibilidade).
- **Fluxo com dados** (criar aluno, fazer chamada, etc.) → prefira criar o dado no
  próprio teste e limpá-lo ao final, para não depender de estado compartilhado.
- **Backlog de a11y** que não dá para corrigir agora → ele já sai no relatório como
  `minor`/`moderate` sem travar o merge; só vira gate quando for `serious`/`critical`.

[Playwright]: https://playwright.dev
[axe-core]: https://github.com/dequelabs/axe-core
