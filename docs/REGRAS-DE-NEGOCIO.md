# Regras de negócio — EBD Samambaia

> Catálogo das regras de negócio implementadas no app (a fonte de verdade continua sendo o
> código na camada `backend/.../service/`). Este documento descreve **o que o sistema decide
> sozinho** — cálculos, transições de status, automações — para que qualquer pessoa entenda o
> comportamento sem ler o código. Ao mudar uma regra, atualize aqui.
>
> Convenções: valores entre `código` são constantes/nomes reais; "aula/prova futura" = com data
> **posterior a hoje** (nunca conta); todos os cálculos ignoram o próprio professor da aula.

---

## Sumário

1. [Chamada (presença)](#1-chamada-presença)
2. [Inativação automática do aluno](#2-inativação-automática-do-aluno)
3. [Faltas justificadas](#3-faltas-justificadas)
4. [Desafios / Ranking](#4-desafios--ranking)
5. [Provas e notas](#5-provas-e-notas)
6. [Quiz / prova online (auto-correção)](#6-quiz--prova-online-auto-correção)
7. [Boletim](#7-boletim)
8. [Visitantes e promoção a aluno](#8-visitantes-e-promoção-a-aluno)
9. [Acesso automático do aluno (login)](#9-acesso-automático-do-aluno-login)
10. [Regras de login (username)](#10-regras-de-login-username)
11. [Papéis, capacidades e escopo por turma](#11-papéis-capacidades-e-escopo-por-turma)
12. [Autenticação, senha e recuperação](#12-autenticação-senha-e-recuperação)
13. [Aulas](#13-aulas)
14. [Requisições da tesouraria](#14-requisições-da-tesouraria)
15. [Campanhas (e-mail em massa)](#15-campanhas-e-mail-em-massa)
16. [Rotinas automáticas (agendadas)](#16-rotinas-automáticas-agendadas)
17. [Notificações por e-mail](#17-notificações-por-e-mail)

---

## 1. Chamada (presença)

Fonte: `ChamadaService`.

- A chamada de uma aula avalia **4 itens por aluno**: presente, trouxe a Bíblia, trouxe a
  revista, estudou a lição.
- Só entram na chamada os **alunos ativos da turma** da aula.
- Salvar é **upsert**: uma linha de presença por aluno por aula (cria ou atualiza).
- **Quem dá aula não é contabilizado como aluno naquele dia**: se um aluno está vinculado a um
  usuário que é **professor de alguma aula na data da chamada** (nesta turma **ou em qualquer
  outra**), esse aluno aparece marcado como "dando aula", vem **desabilitado** e qualquer
  presença dele naquele dia é removida ao salvar — não conta na chamada nem no ranking. Num dia
  em que **não** dá aula, ele conta normalmente como aluno. (Antes valia só na aula que ele
  mesmo dava; agora abrange também o dia em que dá aula em outra turma.)
- Marcar um aluno como **presente** limpa automaticamente uma eventual marca de falta
  justificada (motivo/data zerados).

---

## 2. Inativação automática do aluno

Fonte: `ChamadaService.inativarPorFaltasSeguidas`.

- Constante: `MAX_FALTAS_SEGUIDAS = 4`.
- Regra: um aluno que **ultrapassa 4 faltas consecutivas sem justificativa** — ou seja, na
  **5ª falta seguida** — é **inativado automaticamente** ao salvar a chamada.
- A sequência é contada caminhando das aulas **mais recentes para as mais antigas**, terminando
  na aula que está sendo salva, considerando só aulas com data ≤ a dela.
- **Uma presença OU uma falta justificada zera a sequência** (quebra a contagem).
- A verificação só roda para quem **faltou nesta aula** (apenas a falta mais recente pode fechar
  uma sequência).
- Ao inativar: grava **auditoria**, envia **e-mail** ao aluno (best-effort, respeita o toggle) e
  mostra um **alerta no toast** para quem salvou a chamada.
- Reativar o aluno é manual (edição do cadastro).

---

## 3. Faltas justificadas

Fonte: `ChamadaService` (marcação pelo professor) + peso no `DesafiosService`. A visão do aluno
em `MinhaFrequenciaService` é **somente leitura**.

- Quem justifica uma falta é o **professor**, na tela de **chamada**: cada item do
  `SalvarChamadaRequest` traz `justificada` + `justificativaMotivo` (até 300 chars).
- A justificativa só vale para quem **faltou** naquela aula; marcar como **presente** sempre
  limpa a justificativa (motivo/data zerados).
- O **aluno** apenas **visualiza** suas justificativas em `/minha-frequencia` — não há mais
  autosserviço (os endpoints `POST/DELETE /api/me/frequencia/{aulaId}/justificar` foram removidos).
- Efeito no ranking: uma falta justificada passa a valer **0,3** (30% de uma presença) nos
  cálculos de *menos faltou* e da *classificação geral* (ver seção 4).
- Efeito na inativação: falta justificada **zera** a sequência de faltas (seção 2).

---

## 4. Desafios / Ranking

Fonte: `DesafiosService`. Endpoint: `/api/desafios` (filtro opcional por turma e por
`ano`+`trimestre`); resumo do próprio aluno em `/api/me/ranking`.

### 4.1 Período e base de dados

- Sem `ano`+`trimestre` → período **aberto** (de 2000-01-01 até **hoje**).
- Com `ano`+`trimestre` → recorte do trimestre (`PeriodoLetivo`): **1=Jan–Mar, 2=Abr–Jun,
  3=Jul–Set, 4=Out–Dez**.
- Só contam **aulas/provas com data ≤ hoje** e dentro do período (aulas futuras nunca entram).
- Considera apenas **alunos ativos** da turma (ou de todas, se sem filtro de turma).
- O aluno-professor de uma aula é **excluído** das contagens daquela aula.

### 4.2 Métricas acumuladas por aluno

Somadas no período: `presenças`, `bíblia`, `revista`, `lição`, `justificadas` (faltas
justificadas) e `visitantes` (do cadastro de visitantes, campo "trazido por").

### 4.3 Os rankings publicados

| Ranking | Valor ordenado (desc) | Observação |
|---|---|---|
| **Menos faltou** | `presenças + 0,3 × justificadas` (1 casa decimal) | lista todos |
| **Mais trouxe a Bíblia** | nº de vezes | oculta quem tem 0 |
| **Mais trouxe a revista** | nº de vezes | oculta quem tem 0 |
| **Mais estudou a lição** | nº de vezes | oculta quem tem 0 |
| **Mais trouxe visitante** | nº de visitantes | oculta quem tem 0 |
| **Melhores notas** | **média** das notas (2 casas) | só quem tem nota |
| **Classificação geral** | pontuação total (ver 4.5) | lista todos |

### 4.4 Empates e desempate

- Empate no valor principal → aplica-se um **desempate por peso**, tudo decrescente, nesta ordem:
  **lição > visitante > presença > Bíblia > revista**.
- **Empate total** (todos os quesitos iguais) → **mesma posição** (posições podem "pular":
  1, 1, 3…).

### 4.5 Classificação geral (pontuação total)

Soma de todos os quesitos por aluno:

```
total = presenças
      + 0,3 × faltas_justificadas
      + bíblia + revista + lição
      + 2 × visitantes
      + pontos_de_notas
```

- **1 ponto** por presença, Bíblia, revista e lição.
- **0,3 ponto** por falta justificada.
- **2 pontos** por visitante trazido.
- **Pontos de notas**: `Σ (nota / notaMáxima) × 5` sobre as provas do período (cada prova vale
  até 5 pontos, independentemente da escala da nota).
- Total arredondado a 1 casa decimal.
- Desempate: peso (4.4) e, por fim, os pontos de notas; empate total → mesma classificação.

O `/api/me/ranking` devolve, para o aluno logado, o **pódio (top 3)** da classificação geral da
turma dele + a **posição dele**.

---

## 5. Provas e notas

Fonte: `ProvaService`. Uma prova tem `tipo` **OFFLINE** (nota lançada à mão) ou **ONLINE**
(quiz auto-corrigido — seção 6).

- CRUD da prova exige escopo da turma; excluir a prova remove as notas em cascata.
- **Lançar notas** (`salvarNotas`): a grade lista os alunos elegíveis da turma.
  - **Prova OFFLINE**: só entram os alunos **presentes na aula da data da prova** (mesma turma).
    Lançar nota para um ausente é rejeitado (erro 400). Prova ONLINE: todos os ativos.
  - Nota **em branco** → remove o registro existente daquele aluno (se houver).
  - Nota **acima da nota máxima** da prova → erro 400.
- **Lançar e notificar** (`notificarNotas`): envia a cada aluno com nota lançada o e-mail do
  desempenho, respeitando o **opt-in** (`recebeNotificacoes` + e-mail). **Dedup**: se a mesma
  nota já foi notificada (`notificadaNota`), não reenvia.

---

## 6. Quiz / prova online (auto-correção)

Fontes: `QuizService` (professor monta) e `QuizAlunoService` (aluno responde).

### 6.1 Montagem (professor)

- Salvar as questões **substitui todas** as anteriores e marca a prova como **ONLINE**.
- A **nota máxima** da prova vira a **soma dos pontos** das questões (cada questão vale ≥ 1;
  default 1).
- Validações por questão:
  - Tipo `MULTIPLA` (múltipla escolha) ou `VF` (verdadeiro/falso).
  - `VF` → exatamente **2** alternativas; `MULTIPLA` → **pelo menos 2**.
  - Exatamente **1 alternativa correta**; nenhuma alternativa sem texto; enunciado obrigatório.

### 6.2 Resposta e correção (aluno)

- O aluno só vê as provas ONLINE da **sua turma** (o id do aluno vem do vínculo do usuário,
  nunca do request).
- **Janela opcional**: `abreEm`/`fechaEm`. Status derivado: `FUTURA` (antes de abrir),
  `DISPONIVEL`, `FECHADA` (após o prazo), `RESPONDIDA`.
- **1 tentativa só**: se já existe submissão, bloqueia responder de novo.
- Responder exige janela aberta (senão 403).
- **Correção automática**: para cada questão, acerto = alternativa escolhida é a correta.
  Alternativa inválida/inexistente conta como não respondida (0). A nota é a soma dos pontos das
  questões acertadas.
- A correção grava a submissão + as respostas e faz **upsert em `NotaProva`** → alimenta
  boletim, rankings e e-mail de nota.
- **E-mail de desempenho** ao aluno respeita o opt-in; se enviado, marca dedup (`notificadaNota`).
- O aluno pode reabrir o **resultado** já respondido (nota + gabarito).

---

## 7. Boletim

Fonte: `BoletimService`. Boletim por **aluno + ano + trimestre** (mesma divisão trimestral da
seção 4.1). Aulas/provas contam só até **hoje** (não conta as futuras do trimestre corrente).

- **Aproveitamento**: média dos percentuais `nota/notaMáxima × 100` das provas com nota lançada
  (1 casa). **Média das notas**: média aritmética das notas lançadas (2 casas).
- **Frequência**: `percentual = presenças / totalAulas × 100`.
- O boletim mostra apenas **notas, aproveitamento e frequência** — **não há mais veredito de
  situação** (Aprovado / Em recuperação / Trimestre em andamento). Foi removido a pedido dos
  professores para não constranger o aluno.

---

## 8. Visitantes e promoção a aluno

Fonte: `VisitanteService`.

- Ao registrar um visitante numa aula: envia **boas-vindas** ao visitante e **avisa os
  professores** (best-effort, respeita toggle).
- Pode registrar quem o **trouxe** (aluno) — é essa a fonte do ranking "mais trouxe visitante".
- **Promoção automática a aluno** (`AULAS_PARA_PROMOVER = 3`):
  - Um visitante que compareceu às **3 aulas mais recentes da turma** (a atual + as 2 anteriores
    com data ≤ a dela) **vira aluno** automaticamente.
  - **Identidade** entre aulas = **nome normalizado igual E** (telefone **ou** e-mail
    coincidindo). Sem telefone e sem e-mail → **não promove** (não há como confirmar identidade).
  - Idempotente: não promove se já existe aluno equivalente na turma.
  - Ao promover: cria o aluno (ativo, na turma), gera **login automático** (seção 9), grava
    auditoria e envia e-mail de boas-vindas como aluno.

---

## 9. Acesso automático do aluno (login)

Fonte: `AcessoAlunoService`.

- **Todo aluno cadastrado ganha um usuário ALUNO vinculado**, criado na hora.
- **Senha padrão**: `SENHA_PADRAO = "12345678"`, com **troca obrigatória no 1º acesso**
  (`precisaTrocarSenha`).
- **Login gerado**: `nome.sobrenome` sem acento, minúsculas; em colisão, sufixo numérico
  (`joao.silva`, `joao.silva2`, …). Limite de 55 chars na base (folga para o sufixo; máx. 60).
- O usuário **espelha** o estado do aluno: `ativo` e `email` são sincronizados; a senha nunca é
  redefinida por essa sincronização.
- Excluir o aluno **remove** o login vinculado.
- **Backfill idempotente** no boot: cria login para todo aluno que ainda não tem
  (`garantirAcessoParaTodos`).
- O login pode ser **customizado/editado** no cadastro do aluno ou na tela de Usuários
  (`definirLogin`), respeitando as regras da seção 10.

---

## 10. Regras de login (username)

Fonte: `LoginService`. Vale para qualquer usuário.

- **Normalização**: trim + minúsculas.
- **Formato**: só `[a-z0-9]` e separadores simples `.` `-` `_`, sem espaço/acento, sem começar
  ou terminar com separador nem repeti-lo. Ex.: `joao.silva`.
- **Tamanho**: entre `MIN = 3` e `MAX = 60`.
- **Único**: não pode colidir com o login de outro usuário (409).

---

## 11. Papéis, capacidades e escopo por turma

Fontes: `EscopoService`, `TokenService`, `UsuarioService`.

- Papéis são **flags acumuláveis** num único usuário: `ehAdmin`, `ehProfessor`, `ehAluno`,
  `ehTesoureiro`, `ehLider`. Um mesmo login pode ser, por exemplo, professor **e** aluno.
- O **JWT** emite um grupo por flag. O **ADMIN recebe todas as capacidades** por padrão
  (`TESOUREIRO` e `LIDER` inclusas), então os `@RolesAllowed` continuam valendo.
- **Escopo por turma**:
  - **ADMIN** → acesso a todas as turmas.
  - **PROFESSOR** → só as turmas vinculadas a ele (`professor_classe`); acessar outra turma → 403.
  - **ALUNO** → só o próprio cadastro/presenças (o id vem do vínculo do usuário, nunca do
    request).
- **Vínculos** (na edição de usuário): `alunoId` só se for ALUNO ou PROFESSOR; turmas
  (`classeIds`) só para PROFESSOR.

---

## 12. Autenticação, senha e recuperação

Fontes: `AuthService`, `ProtecaoLoginService`, `UsuarioService`, `RecuperacaoSenhaService`.

### 12.1 Login

- Login válido só para usuário **ativo** com senha (bcrypt) correta; mensagem genérica
  ("Usuário ou senha inválidos") em qualquer falha.
- O JWT dura `ebd.jwt.duration-seconds` (**default 28800s = 8h**).
- A resposta informa flags de papéis e `precisaTrocarSenha`.

### 12.2 Anti-força-bruta

- `MAX_TENTATIVAS = 5` falhas seguidas por usuário → **bloqueio de 60s** (`BLOQUEIO`), com HTTP
  **429** informando o tempo restante.
- Falhas antigas expiram após `JANELA = 15min`. Sucesso zera o contador.
- Estado **em memória** (1 instância; zera no restart) — objetivo é frear automação, não ser WAF.

### 12.3 Senha

- **Mínimo 8 caracteres** (`SENHA_MIN`) em toda a aplicação.
- **Trocar a própria senha**: exige a senha atual correta, respeita o mínimo e **recusa repetir
  a senha vigente**. Concluir a troca zera `precisaTrocarSenha`.
- **Não é possível excluir o último administrador** (409).
- **Não é possível excluir usuário** que abriu requisições de tesouraria (409 — reatribua/exclua
  as requisições antes).

### 12.4 Recuperação ("esqueci a senha/usuário")

- O usuário informa o e-mail; para **cada conta ativa** com aquele e-mail gera-se um **token de
  uso único** (guarda-se só o hash SHA-256) e envia-se e-mail com o usuário + link.
- **Nunca revela** se o e-mail existe (anti-enumeração).
- Token expira em `ebd.reset.validade-minutos` (**default 60min**) e é de **uso único**.
- Redefinir exige senha ≥ 8 chars e zera `precisaTrocarSenha`.

---

## 13. Aulas

Fonte: `AulaService`.

- **Uma aula por turma por data** (data única): tentar criar/editar para uma data já usada na
  mesma turma → 409.
- O **professor** da aula, se informado, precisa ser um usuário com `ehProfessor` (senão 400).
- Excluir a aula remove as presenças em cascata.

---

## 14. Requisições da tesouraria

Fonte: `RequisicaoService`. Papéis: **LIDER** abre, **TESOUREIRO** avalia.

### 14.1 Ciclo de vida (status)

```
ABERTA ──aprovar──▶ APROVADA ──finalizar──▶ FINALIZADA
   │
   ├──negar────▶ NEGADA
   └──cancelar──▶ CANCELADA
```

- **Número** único por ano: `REQ-<ano>-<seq4>` (ex.: `REQ-2026-0007`).
- **Aprovar** (tesoureiro, só de ABERTA): define valor aprovado (default = solicitado; deve ser
  > 0), parecer opcional e comprovante opcional. → APROVADA.
- **Negar** (só de ABERTA): parecer opcional. → NEGADA.
- **Cancelar** (só o dono/ADMIN, só de ABERTA). → CANCELADA.
- **Finalizar** (só o dono/ADMIN, só de APROVADA): exige **ao menos 1 anexo de nota fiscal** +
  valor gasto. → FINALIZADA.

### 14.2 Troco (devolução)

- **Troco devido** = valor aprovado − valor gasto (0 se gastou tudo/mais ou não informou gasto).
- Se houver troco, **é obrigatório anexar o comprovante da devolução** ao PIX da igreja para
  finalizar (senão 400).

### 14.3 Forma de repasse e chave PIX

- Forma: **DINHEIRO** (default) ou **PIX**.
- Chave PIX só **CPF, e-mail ou telefone** — **aleatória é recusada**.
- A chave precisa ser **do próprio solicitante**:
  - **E-mail** → deve bater com o e-mail cadastrado do solicitante.
  - **Telefone** → deve bater (só dígitos) com o telefone do aluno vinculado.
  - **CPF** → como o sistema não guarda CPF, valida apenas 11 dígitos; o tesoureiro confere no
    comprovante.

### 14.4 Visibilidade e anexos

- **ADMIN e TESOUREIRO** veem todas as requisições; o **líder** vê só as próprias.
- Anexos têm categoria **NOTA_FISCAL** ou **COMPROVANTE**, guardados como binário (`bytea`);
  download valida o acesso.
- Cada transição dispara e-mail: nova → tesoureiros; avaliação → solicitante; finalização →
  tesoureiros.

### 14.5 Cobrança de nota fiscal

- Rotina diária (`CobrancaNotaService`, seção 16): cobra por e-mail o solicitante de cada
  requisição **APROVADA sem nota**. **Dedup por dia** (`notaCobradaEm`): no máx. 1 e-mail por
  requisição por dia.

---

## 15. Campanhas (e-mail em massa)

Fonte: `CampanhaService`. Envia e-mail aos alunos com **opt-in**, com imagens inline opcionais.

- Requer o envio de e-mail **habilitado** no servidor (`ebd.notificacoes.enabled`), senão 409.
- **Texto**: título obrigatório ≤ 150 chars; mensagem obrigatória ≤ 5000 chars.
- **Imagens**: até `MAX_IMAGENS = 5`, cada uma ≤ **2 MB**, tipos `image/jpeg|png|gif|webp`.
- Destinatários: alunos com e-mail + opt-in (opcionalmente filtrados por turma).
- Registra a campanha (histórico) com o total de e-mails enviados.

---

## 16. Rotinas automáticas (agendadas)

Fuso: **America/Sao_Paulo** (BRT). Instância única; sem recuperação de "misfire" (se a VM
estiver fora do ar no horário, aquele disparo não é reenviado).

| Rotina | Horário (cron default) | O que faz | Dedup |
|---|---|---|---|
| **Aniversário** (`AniversarioService`) | **12:00** BRT | e-mail de feliz aniversário aos alunos com e-mail que fazem aniversário no dia | — |
| **Cobrança de nota** (`CobrancaNotaService`) | **09:00** BRT | cobra nota fiscal de requisições APROVADAS sem nota | por dia (`notaCobradaEm`) |

Ambos os cron são configuráveis (`ebd.aniversario.cron`, `ebd.cobranca-nota.cron`).

---

## 17. Notificações por e-mail

Fonte: `NotificacaoService` + `EmailDispatcher`.

- **Toggle global**: `ebd.notificacoes.enabled` (desligado em produção até o SMTP estar
  configurado). Com o toggle off, nada é enviado.
- **Opt-in do aluno**: e-mails de chamada/nota respeitam `recebeNotificacoes` + e-mail
  cadastrado.
- **Envio assíncrono** (EventBus): a resposta HTTP não espera o SMTP. "E-mail disparado" =
  **enfileirado**; falha de SMTP em background só gera log (não re-tenta). A **contagem/dedup é
  síncrona** (só banco), então os números relatados continuam válidos.
- Todo envio é **best-effort**: falha de e-mail nunca quebra a operação de negócio (salvar
  chamada, cadastrar visitante etc.).

Eventos que geram e-mail: chamada salva (alunos), aluno inativado, visitante (boas-vindas + aviso
aos professores), visitante promovido a aluno, nota lançada/quiz corrigido, aniversário,
requisição (nova/avaliada/finalizada/cobrança), campanhas, recuperação de senha.
