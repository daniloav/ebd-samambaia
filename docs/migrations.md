# Migrations do banco (Flyway) — changelog e como reproduzir

O schema do banco é **versionado e gerenciado pelo Flyway**. As migrations ficam em
`backend/src/main/resources/db/migration/` e rodam **sozinhas** quando o backend sobe
(`quarkus.flyway.migrate-at-start=true`). O Hibernate **não** gera o schema
(`hibernate.database.generation=none`) — a fonte da verdade são as migrations.

## Changelog

| Versão | Arquivo | O que faz |
|---|---|---|
| **V1** | `V1__schema.sql` | Schema inicial: `aluno`, `aula`, `presenca`, `prova`, `nota_prova`, `usuario`. |
| **V2** | `V2__presenca_visitante.sql` | Adiciona `trouxe_visitante` à chamada. |
| **V3** | `V3__classes_e_usuario_aluno.sql` | Multi-turma: tabela `classe`, `classe_id` em `aluno/aula/prova`, vínculo `usuario.aluno_id` e role `ALUNO`. Unicidade da aula passa a ser `(classe_id, data)`. |
| **V4** | `V4__aluno_email_notificacoes.sql` | `email` + `recebe_notificacoes` (opt-in/LGPD) em `aluno`. |
| **V5** | `V5__campanha.sql` | Tabela `campanha` (envio de e-mail em massa + histórico). |
| **V6** | `V6__professor_classe.sql` | RBAC: tabela de vínculo N:N `professor_classe` (professor ↔ turma). |
| **V7** | `V7__visitante_e_usuario_email.sql` | Cadastro de visitante e `email` em `usuario`. |
| **V8** | `V8__remove_trouxe_visitante.sql` | Remove `trouxe_visitante` da chamada (visitante vem do cadastro). |
| **V9** | `V9__campanha_imagem.sql` | Tabela `campanha_imagem` (arte/imagens embutidas inline no e-mail da campanha; `bytea`). |
| **V16** | `V16__requisicao_tesouraria.sql` | Módulo de tesouraria: tabelas `requisicao_tesouraria` e `requisicao_anexo` (nota fiscal em `bytea`); amplia `ck_usuario_role` p/ `TESOUREIRO`/`LIDER`. |
| **V17** | `V17__view_requisicoes_integracao.sql` | View somente-leitura `vw_requisicoes_integracao` p/ integração externa da tesouraria (sem senha nem binário). Ver [`integracao-tesouraria.md`](integracao-tesouraria.md). |
| **V18** | `V18__papel_tesouraria_lider_como_flags.sql` | Tesoureiro/líder viram flags `eh_tesoureiro`/`eh_lider`; CHECK de role volta a ADMIN/PROFESSOR/ALUNO. |
| **V19** | `V19__papeis_como_flags.sql` | Achata os papéis: flags `eh_admin`/`eh_professor`/`eh_aluno` (backfill 1:1) e **remove a coluna `role`**. Ver [`unificacao-papeis.md`](unificacao-papeis.md). |
| **V20** | `V20__requisicao_forma_repasse.sql` | Requisição ganha `forma_repasse` (dinheiro/pix) + `pix_tipo`/`pix_chave`; anexo ganha `categoria` (nota fiscal x comprovante); view de integração atualizada (append-only) com forma/pix + `possui_comprovante`. |
| **V24** | `V24__falta_justificada.sql` | Falta justificada: `justificada`/`justificativa_motivo`/`justificada_em` em `presenca`. O aluno justifica a própria falta (vale 0,3 no ranking); base das regras de inativação por faltas seguidas e de promoção de visitante. |
| **V26** | `V26__aula_adiada.sql` | Aula adiada/cancelada: coluna `adiada` (boolean) em `aula`. Aula adiada sai de toda pontuação e retrospecto (chamada, rankings, relatórios, boletim, dashboard, frequência, inativação por faltas e promoção de visitante). |
| **V28** | `V28__pix_beneficiario_terceiro.sql` | Chave PIX de **terceiro** na requisição (oferta de amor): `pix_titular` (PROPRIO/TERCEIRO), `pix_beneficiario_nome` e `pix_beneficiario_obs` em `requisicao_tesouraria`; view `vw_requisicoes_integracao` estendida com essas 3 colunas (CREATE OR REPLACE append-only, preserva o GRANT do usuário read-only). |
| **V30** | `V30__inativacao_aluno.sql` | Histórico de inativação: tabela `aluno_inativacao` (um episódio por saída — `inativado_em`, `motivo`, `faltas_seguidas`, `inativado_por`, `reativado_em`, `reativado_por`), FK `ON DELETE CASCADE` para `aluno`. Faz **backfill** dos alunos hoje inativos: das inativações automáticas recupera data/usuário/nº de faltas da `auditoria`; o resto entra como `NAO_REGISTRADO` sem data. |
| **V29** | `V29__lembrete_chamada_pendente.sql` | Lembrete de chamada pendente: coluna `chamada_cobrada_em` (timestamp) em `aula`, com o último disparo do lembrete horário ao professor (dedup: no máximo 1 e-mail por aula por hora). |

## Regras de ouro

- **Nunca edite uma migration já aplicada** (V1…V6). Toda mudança de schema é uma **nova**
  migration `V{n}__descricao.sql` (a próxima seria `V7__...`).
- O Flyway registra o que já aplicou na tabela de controle **`flyway_schema_history`** (criada
  por ele no próprio banco). Migration já aplicada **nunca roda de novo**.
- SQL é específico de **PostgreSQL** (`BIGSERIAL`, `NOW()`, `TEXT`).

## Trocar / recriar o banco — preciso re-executar as migrations?

Você **nunca** roda migration manualmente; o Flyway faz no boot. O que acontece depende do caso:

- **Banco novo/vazio** (nova instância Postgres limpa): aponte o backend para ele e, no
  **primeiro boot**, o Flyway aplica **V1 → V6 em ordem**, recriando o schema do zero.
  Em seguida o `DataInitializer` cria o seed (admin/professor). Nada manual.
  > É o ponto do Flyway: **as migrations *são* o banco reproduzível**.

- **Migrar os dados junto** (`pg_dump` → `pg_restore` para outro Postgres): o dump já leva a
  tabela `flyway_schema_history`, então o Flyway vê que V1–V6 já foram aplicadas e **não
  re-roda** — só aplicaria migrations novas (V7+). Sem risco de duplicar.

- **Trocar o motor** (Postgres → MySQL/Oracle etc.): aí **daria trabalho** — o SQL precisaria
  ser adaptado ao novo dialeto. De um Postgres para outro é tranquilo.

### Reproduzir o banco do zero (dev)

```bash
# 1) sobe um Postgres vazio (nativo ou docker) com role/db 'ebd'
# 2) sobe o backend — o Flyway aplica V1..V6 e o seed cria os usuários
cd backend && mvn quarkus:dev
# conferir o histórico aplicado:
#   psql -d ebd -c "select version, description, success from flyway_schema_history order by installed_rank;"
```

## Backup e restauração (produção)

A cada deploy, o CD faz um **backup automático** do banco **antes** de aplicar migrations
(`scripts/backup-db.sh`): um `pg_dump` gzipado em `~/ebd-samambaia/backups/` na VM (mantém
os 10 mais recentes). Se o backup falhar, o deploy é **abortado** — sem ponto de restauração
é arriscado subir migration.

**Restaurar** um backup (na VM, em `~/ebd-samambaia`):

```bash
gzip -dc backups/ebd-AAAAMMDD-HHMMSS.sql.gz \
  | docker compose exec -T db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

> Restaurar sobrescreve os dados atuais. Se uma migration der problema: restaure o dump,
> corrija com uma **nova** migration (nunca edite a já aplicada) e deploie de novo.

## Config relevante (`application.properties`)

```properties
quarkus.hibernate-orm.database.generation=none   # schema é do Flyway, não do Hibernate
quarkus.flyway.migrate-at-start=true              # aplica pendentes no boot
quarkus.flyway.baseline-on-migrate=true           # tolera banco pré-existente sem histórico
```
