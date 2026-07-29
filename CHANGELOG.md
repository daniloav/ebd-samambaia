# Changelog

Todas as mudanças relevantes do **EBD ICES** (Escola Bíblica Dominical — ICE Samambaia).

O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o
versionamento é **[SemVer](https://semver.org/lang/pt-BR/)**, gerado automaticamente pela
esteira de CD a cada merge na `main` (commits `chore(release): vX.Y.Z`).

> **Geração automática:** a cada release, o CD publica um **GitHub Release** com as notas
> derivadas dos *Conventional Commits* (ver `scripts/gerar-changelog.sh`). Este arquivo é a
> versão curada — para atualizar a seção *[Não lançado]* rode
> `bash scripts/gerar-changelog.sh preview` e ajuste antes de abrir o PR de release.

Datas no formato `AAAA-MM-DD` (fuso BRT).

## [Não lançado] — branch `develop`

### Adicionado
- **Falta justificada (30%) + status automático** (migration **V24**):
  - Aluno **justifica a própria falta** em `/minha-frequencia` (autosserviço); a falta
    justificada passa a valer **0,3** no ranking *menos faltou* e na classificação geral.
  - Ao salvar a chamada, aluno com **>4 faltas seguidas sem justificativa** é **inativado**
    automaticamente (com alerta no toast + Auditoria).
  - Visitante que compareceu às **3 aulas seguidas** mais recentes da turma **vira aluno**
    com login automático.
  - Ambos os eventos automáticos disparam e-mail (respeitando `ebd.notificacoes.enabled`).

---

## [1.25.0] — 2026-07-28
### Adicionado
- **Tesouraria:** comprovante de **devolução do troco** na finalização da requisição.

## [1.24.2] — 2026-07-28
### Corrigido
- **Tesouraria:** copiar chave PIX na aprovação e corrigir abertura do comprovante.
- **PWA:** app **auto-recuperável** quando um bundle com hash antigo some após deploy
  (evita tela branca).
- **Scripts:** `consolidar-contas.sh` (consolidação de contas duplicadas + remoção de
  tesoureiro/líder) auto-detecta `psql` (prod ou dev local) e usa `docker exec ebd-postgres`
  por padrão.

## [1.24.1] — 2026-07-28
### Corrigido
- **Usuários:** permite **excluir usuário com requisições** (avaliador vira `SET NULL`;
  erro claro quando o usuário é solicitante).

## [1.24.0] — 2026-07-27
### Adicionado
- **Recuperação de senha/usuário por e-mail** (link com token).
- **Tutoriais:** guia "Recuperar acesso" na central.

## [1.23.0] — 2026-07-27
### Adicionado
- **Tutoriais:** guias dedicados **para os alunos** na central de tutoriais.

## [1.22.2] — 2026-07-27
### Corrigido
- **Tesouraria:** líder passa a **enxergar o comprovante** (sinalizado na lista).
- **Segurança:** resolvidos os alertas do **CodeQL** (code scanning).
- **CD:** o bump de versão não commita mais na `main` (protegida) — apenas cria a tag.

## [1.22.1] — 2026-07-27
### Infra / Docs
- Documentação do **runner self-hosted** (Docker numa VM OCI) para economizar minutos do
  GitHub Actions (script + runbook).
- Decisão registrada de tornar o repositório **público** (pré-requisito: limpar segredos do
  histórico — `*.pem` purgado via `filter-repo` + force-push).

## [1.22.0] — 2026-07-27
### Adicionado
- **Tesouraria — forma de repasse** (migration **V20**): na abertura o líder escolhe
  **dinheiro ou PIX** (chave CPF/e-mail/telefone validada como do próprio solicitante);
  na aprovação o tesoureiro pode anexar o **comprovante de transferência**.

## [1.21.0] — 2026-07-27
### Adicionado
- **Papéis unificados (capacidades)** (migration **V19**): ADMIN/PROFESSOR/ALUNO viram
  **flags** — um único usuário acumula papéis (ex.: professor + aluno no mesmo login).
  Frontend com **checkboxes** e alternador **"Atuando como"** (Gestão ↔ Aluno).
### Infra
- CI: pull das imagens de scan **resiliente** (retry) — evita flake do Docker Hub.

## [1.20.0] — 2026-07-27
### Adicionado
- **Rankings/Relatório:** filtro por **trimestre/período**.

## [1.19.0] — 2026-07-27
### Adicionado
- **Envio de e-mail assíncrono via EventBus** (Vert.x): `mailer.send()` desacoplado do
  fluxo HTTP — a resposta da chamada não espera mais o SMTP; e-mails da tesouraria saem
  fora da transação do banco.

## [1.18.1] — 2026-07-27
### Alterado
- **Tesoureiro e líder viram capacidades (flags)** sobre a role base (migration **V18**:
  `eh_tesoureiro`/`eh_lider`). O ADMIN recebe as duas por padrão.

## [1.18.0] — 2026-07-27
### Adicionado
- **Integração externa da tesouraria:** view read-only `vw_requisicoes_integracao`
  (migration **V17**) + runbook e script de setup (usuário `tesouraria_ro`
  least-privilege, acesso via **túnel SSH**).

## [1.17.0] — 2026-07-27
### Adicionado
- **Módulo de Requisições da Tesouraria** (migration **V16**): **LIDER** solicita recurso
  (nº `REQ-<ano>-<seq4>`) → **TESOUREIRO** aprova/nega → **prestação de contas** com
  nota(s) fiscal(is) (PDF/imagens em `bytea`) → FINALIZADA. Inclui **cobrança diária por
  e-mail** de notas pendentes e mensageria por evento. Página `/requisicoes`, `tesourariaGuard`
  e grupo **Tesouraria** no menu.

## [1.16.0] — 2026-07-27
### Adicionado
- **Ranking resumido da turma para o aluno** (pódio + sua posição).

## [1.15.0] — 2026-07-27
### Alterado
- **Chamada/Ranking:** o **professor da aula** não conta na chamada nem no ranking.

## [1.14.0 – 1.14.1] — 2026-07-26
### Adicionado
- **Tutoriais:** guias de Chamada, Alunos, Provas e Boletim.
### Corrigido
- **Notificações:** dedup em todos os e-mails de evento (nota, aniversário) — sem reenvio.

## [1.13.0] — 2026-07-23
### Adicionado
- **Central de tutoriais** hospedada no app + link na tela de login.

## [1.12.0 – 1.12.1] — 2026-07-23
### Adicionado
- **Auditoria:** registra quem criou/alterou/excluiu aluno, aula, prova e usuário.
### Corrigido
- **Boletim:** trimestre em andamento não mostra mais "Em recuperação".

## [1.11.0] — 2026-07-23
### Adicionado
- **Login editável** (username `nome.sobrenome`) no cadastro do aluno e na tela de
  usuários, com validações centralizadas no `LoginService`.

## [1.10.0] — 2026-07-23
### Adicionado
- **UX mobile:** menu vira **gaveta lateral** (off-canvas drawer) no celular.
### Corrigido
- **Boletim:** frequência considera só aulas já realizadas (exclui futuras).

## [1.9.0] — 2026-07-22
### Adicionado
- **Acesso automático do aluno** (migration **V11**): todo aluno cadastrado ganha **login**
  (senha padrão `12345678` + troca obrigatória no 1º acesso). Backfill idempotente no boot.
### Corrigido
- **Desafios:** ranking considera só aulas já realizadas.

## [1.8.0] — 2026-07-22
### Adicionado
- **Quiz / prova online auto-corrigida** (migration **V10**): prova ganha `tipo`
  OFFLINE/ONLINE. Professor monta questões (múltipla escolha e V/F) em `/provas/:id/questoes`;
  aluno responde em `/minhas-provas` (1 tentativa + janela opcional) e a correção grava a nota
  (→ boletim, rankings e e-mail).

## [1.7.0] — 2026-07-22
### Adicionado
- **Dashboard** com gráficos de frequência e distribuição.
- **Primeira suíte de testes** `@QuarkusTest` (auth, chamada, prova, relatório, e-mail,
  rankings, boletim) + gate no CI.

## [1.6.0] — 2026-07-21
### Adicionado
- **Campanhas:** anexar imagens/artes embutidas **inline** no e-mail.

## [1.5.0 – 1.5.1] — 2026-07-21
### Adicionado
- **Pacote de UX:** modal de confirmação, busca, estados vazios, **modo escuro**,
  mobile-cards e ícones PNG.
### Corrigido
- **Compatibilidade:** build mirava só iOS 18+ (tela branca em Safari antigo) + tela de
  fallback.

## [1.4.0 – 1.4.2] — 2026-07-21
### Adicionado
- **Relatório de visitantes** por período (geral/turma, export PDF/Excel), **batch de
  aniversário** (`quarkus-scheduler`, 12:00 BRT) e **boletim por trimestre** + e-mail de nota.
### Infra
- **Estágio 1:** build no **CI** + imagens no **GHCR** (fim do build lento na VM); JWT por volume.
- **Estágio 2:** split do Postgres em **2ª VM** (`ebd-db`) com script de cutover.
- **Backups:** agendado na `ebd-db` + **offsite** no OCI Object Storage (PAR).

## [1.3.0 – 1.3.2] — 2026-07-21
### Adicionado
- **Segurança P2:** senha mínima (8), trocar a própria senha (`PUT /api/me/senha` + tela
  *Minha conta*) e **chaves JWT persistentes** opt-in via secrets `EBD_JWT_*`.
### Alterado
- **E-mail:** migração Brevo → **SMTP do Gmail**.

## [1.2.0] — 2026-07-21
### Adicionado
- **Segurança P1:** CORS fechado, **anti-força-bruta** no login, headers no Caddy, aviso de
  sessão expirada e chamada por **toque**.

## [1.1.0 – 1.1.5] — 2026-07-20 a 2026-07-21
### Adicionado
- **Versionamento SemVer automático** por commit no deploy; app renomeado para **"EBD ICES"**
  exibindo a versão.
- **Exportação** de relatórios para **Excel (.xlsx)** e **PDF** (geral e por aluno).
### Corrigido
- **Desafios:** desempate por peso + lista completa; visitante conta só como presente.
### Infra
- **Backup automático** (`pg_dump`) antes de cada deploy; CI valida migrations contra
  Postgres real (Flyway clean travado); faxina do Docker na VM pós-deploy.

---

## Pré-versionamento — 2026-07-18 a 2026-07-20

Antes da adoção do SemVer automático (tag `v1.1.0`):

### Módulos e features
- **MVP** — módulos de **Chamada** (presença + 4 itens por aluno, CRUD de alunos,
  relatório) e **Desafios** (rankings + submódulo **Provas** com notas).
- **Classes (multi-turma)** e **Usuários** (roles + ALUNO).
- **RBAC:** escopo por classe para o PROFESSOR (N:N) e visão própria do ALUNO.
- **Visitantes:** cadastro com e-mails (boas-vindas/aviso aos professores) + **relatório
  geral do dia**.
- **Alertas por e-mail** ao salvar a chamada (opt-in por aluno, migration V4); e-mail HTML
  diferenciado presente × ausente.
- **PWA** instalável com offline básico.
- **Campanhas:** envio de e-mail em massa.
- **Tela de Aulas** (CRUD).

### Infra / segurança / docs
- **HTTPS** via Caddy (Let's Encrypt) em `ebd-ices.duckdns.org`.
- Esteira **CI/CD** (GitHub Actions: build + Semgrep/Trivy/gitleaks; CD para a VM OCI).
- **Segredos removidos** do versionamento; fluxo de branches `develop` → `main`.
- Documentação completa em `docs/` (arquitetura, topologia, migrations, API, roadmap,
  runbooks de deploy).
