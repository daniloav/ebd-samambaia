# Roadmap e backlog — EBD Adultos

Lista viva de próximos passos e ideias. Marque o que for concluindo e adicione novas ideias.

## 🔴 Prioridade imediata

- [ ] **Validar o runtime com Postgres real** (migrations, seed, login, chamada, rankings).
      Ainda não executado ponta-a-ponta. Ver CLAUDE.md §9.
- [ ] **Concluir o deploy na OCI** (aguardando capacidade A1; retry rodando) e publicar o site.
- [ ] Após deploy: **trocar as senhas padrão** (`admin`/`professor`) e as credenciais do banco.

## 🟡 Evoluções funcionais (curto prazo)

- [ ] **Dashboard com gráficos** (frequência ao longo do tempo, distribuição de presença).
- [ ] **Exportar relatório** de presenças para PDF/Excel.
- [ ] **Filtro por trimestre/período letivo** em relatórios e rankings.
- [ ] **Histórico da chamada** por aluno (linha do tempo de presença/itens).
- [ ] **Gestão de usuários** na UI (hoje só via seed): tela de CRUD de `Usuario` (ADMIN).
- [ ] **Aniversariantes do mês** (já temos `data_nascimento`).
- [ ] **Observações por aula/aluno** (campo de texto livre na chamada).

## 🟢 Qualidade e robustez

- [ ] **Testes automatizados**: `@QuarkusTest` para `ChamadaService`, `RelatorioService`,
      `DesafiosService`; testes de fluxo de autenticação.
- [ ] **Testes de front** (ao menos smoke dos serviços/guards).
- [ ] **CI** (GitHub Actions): build backend + frontend a cada push.
- [ ] **Soft-delete de aluno** (preservar histórico usando `ativo`, sem cascata destrutiva).
- [ ] **Paginação** nas listas quando crescer o volume.

## 🔵 Infra e segurança

- [ ] **HTTPS** na VM (Caddy ou certbot) com domínio próprio.
- [ ] **Backups automáticos** do Postgres (cron + `pg_dump`).
- [ ] **Chaves JWT persistentes** via volume (hoje o Docker gera novas a cada build).
- [ ] (Opcional) **Purga de histórico do Git** para remover a chave JWT antiga, se o repo virar público.
- [ ] **Budget/alerta de custo** na OCI (garantia extra contra cobrança).
- [ ] **Proteção de branch na `main`** (bloquear push direto, exigir PR + CI verde).
      Requer **GitHub Pro** (repo privado) ou tornar o repo **público**. Hoje o fluxo
      develop→PR→main é seguido por convenção. Aplicar via `gh api PUT .../branches/main/protection`.
- [ ] **Avaliar upgrade para "Pay As You Go"** para destravar capacidade do **A1** (ARM, 6 GB).
      Contas Free Trial têm baixa prioridade na fila do A1 ("Out of capacity"); o upgrade
      costuma liberar, e os recursos **Always Free continuam US$ 0**. Precisa cadastrar cartão
      (blindar com Budget). Alternativa ao E2.1.Micro (1 GB) que estamos usando agora.

## 🧭 Módulos planejados pós-MVP (avaliados em 2026-07-19)

> Ordem sugerida: **1 → 3 → (canal de mensagens) → 2 → 5 → 4**. Os itens 2, 4 e 5 dependem
> de contatos/consentimento (LGPD) e de usuários com papel de aluno (item 3).

### 1. CRUD de Classes (multi-turma)  — *fundacional*
- Hoje o app é implicitamente "Adultos". Criar entidade **Classe** (nome, descrição, ativo) e
  vincular **Aluno → Classe**; **Aula/Chamada, Provas, Rankings e Relatório passam a ser por classe**.
- Migration `V3` (tabela + FKs), seed de uma classe "Adultos" para os dados atuais, seletor de
  classe na UI. Esforço: **médio-alto**. É base para escalar o sistema para a igreja toda.

### 3. Perfis/roles + CRUD de Usuários  — *fundacional*
- Add role **ALUNO** (hoje há ADMIN/PROFESSOR); tela de **CRUD de usuários** (admin cria/edita,
  define papel e vincula a um Aluno). Aluno logado vê a própria frequência/notas.
- Base para notificações/campanhas/app (o aluno precisa existir como usuário/contato). Esforço: **médio**.

### 2. Alertas por mensagem (presença/falta na chamada) — ✅ e-mail implementado
- Notifica o aluno (ou responsável) quando a chamada é lançada. **Depende de canal + consentimento (LGPD)**.
- **Opções de canal (do mais free/fácil ao mais "desejado"):**
  | Canal | Custo | Facilidade | Observação |
  |---|---|---|---|
  | **Telegram Bot** | **grátis** | fácil | 100% free; aluno precisa iniciar o bot (opt-in) |
  | **E-mail** (Brevo/SMTP) | grátis (300/dia) | fácil | confiável, menos "instantâneo" |
  | **Push (FCM)** | grátis | média | precisa do app/PWA (itens 4/5) |
  | **WhatsApp Cloud API (Meta)** | quase grátis p/ *utility* | **burocrático** | número Business + templates aprovados; pode gerar custo |
  | **SMS** | **pago** | média | sem tier free sustentável no BR |
- Recomendação: começar por **Telegram** ou **e-mail** (grátis de verdade); WhatsApp depois se valer a pena.
- Implementação: `NotificacaoService` disparado no `salvarChamada` (assíncrono), com opt-in por aluno.
- **Feito (canal e-mail):** `Aluno` ganhou `email` + `recebe_notificacoes` (opt-in/LGPD, migration V4);
  `NotificacaoService` envia um e-mail a cada aluno que optou, ao salvar a chamada (`ChamadaResource`).
  Toggle `ebd.notificacoes.enabled` (padrão **off**; **on** em dev com mailer em *mock*). Config SMTP por env.
  **Para ligar em produção**, ver [`docs/notificacoes-email.md`](notificacoes-email.md) (SMTP Brevo grátis).
- **Pendente/roadmap:** envio **assíncrono** (hoje é síncrono na transação da chamada) e outros canais (Telegram/WhatsApp).

### 5. Módulo de Campanhas (envio em massa)
- CRUD de campanha (título, mensagem, público-alvo por classe/filtro) → dispara pelo canal do item 2
  (ou push do app). Reaproveita o `NotificacaoService`. Esforço: **médio**. Depende de 2 e 3.

### 4. App Android/iOS
- As APIs REST+JWT já servem um app. Opções:
  - **PWA** (transformar o Angular atual em instalável/offline): **sem custo de loja**, reaproveita o código — bom **primeiro passo**.
  - **Flutter** (1 código p/ iOS+Android) ou React Native: app nativo "de verdade", porém publicar exige **Apple Developer US$99/ano** e **Google Play US$25** (única vez).
- Recomendação: **PWA primeiro**, nativo depois se necessário. Esforço nativo: **alto**.

## 💡 Ideias maiores (longo prazo)

- [ ] **PWA / uso offline** para fazer chamada sem internet e sincronizar depois.
- [ ] **Múltiplas classes/turmas** (hoje é só a classe de adultos).
- [ ] **Notificações** (WhatsApp/e-mail) para faltas seguidas ou aniversários.
- [ ] **Premiação automática** ao fim do trimestre com base nos rankings.

## Limitações conhecidas (estado atual)

- Exclusão de aluno/aula/prova é **destrutiva** (cascata).
- Rankings/relatórios consideram todos os alunos ativos e todo o período (sem recorte por turma/trimestre).
- Sem testes automatizados; validação só por build.
- Uma única VM roda tudo (db + api + front) — suficiente para o MVP, não para alta disponibilidade.
