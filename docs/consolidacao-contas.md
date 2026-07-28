# Consolidar contas e excluir usuários com requisições (runbook — produção)

Dois ajustes de **dados de produção** (o Claude não executa em prod — você roda). Rode no
Postgres da `ebd-db` (ex.: `docker compose exec db psql -U ebd -d ebd`). **Faça um backup antes**
(o CD já faz backup no deploy; para rodar SQL avulso, gere um `pg_dump` antes).

O SQL abaixo foi **validado no ambiente de dev** com um par de teste equivalente.

---

## 1. Consolidar contas duplicadas (mover as infos de aluno `x.sobrenome` → `x`)

Cada pessoa tem hoje 2 contas: a **auto de aluno** (`nome.sobrenome`, com o vínculo de aluno) e
outra conta (`nome`). Queremos manter a conta `nome`, dando a ela o papel/vínculo de aluno, e
remover a `nome.sobrenome`.

Pares:
- `jaqueline.costa` → `jaqueline`
- `joelma.gadelha` → `joelma`
- `mariana.moura` → `mariana`
- `matheus.lima` → `matheus`

**Confira o estado antes:**
```sql
SELECT username, eh_admin, eh_professor, eh_aluno, aluno_id
FROM usuario
WHERE username IN ('jaqueline','jaqueline.costa','joelma','joelma.gadelha',
                   'mariana','mariana.moura','matheus','matheus.lima')
ORDER BY username;
```

**Consolidação (uma transação, os 4 pares):**
```sql
BEGIN;

-- função-macro "na mão": repete o mesmo bloco trocando os dois nomes.
-- 1) move eh_aluno + aluno_id (+ e-mail, se a conta destino não tiver) da conta antiga p/ a nova
-- 2) reatribui eventuais requisições da conta antiga p/ a nova (normalmente 0)
-- 3) remove a conta antiga

-- jaqueline.costa -> jaqueline
UPDATE usuario dest SET eh_aluno = TRUE, aluno_id = src.aluno_id, email = COALESCE(dest.email, src.email)
  FROM usuario src WHERE dest.username = 'jaqueline' AND src.username = 'jaqueline.costa';
UPDATE requisicao_tesouraria SET solicitante_id  = (SELECT id FROM usuario WHERE username='jaqueline')
  WHERE solicitante_id  = (SELECT id FROM usuario WHERE username='jaqueline.costa');
UPDATE requisicao_tesouraria SET avaliado_por_id = (SELECT id FROM usuario WHERE username='jaqueline')
  WHERE avaliado_por_id = (SELECT id FROM usuario WHERE username='jaqueline.costa');
DELETE FROM usuario WHERE username = 'jaqueline.costa';

-- joelma.gadelha -> joelma
UPDATE usuario dest SET eh_aluno = TRUE, aluno_id = src.aluno_id, email = COALESCE(dest.email, src.email)
  FROM usuario src WHERE dest.username = 'joelma' AND src.username = 'joelma.gadelha';
UPDATE requisicao_tesouraria SET solicitante_id  = (SELECT id FROM usuario WHERE username='joelma')
  WHERE solicitante_id  = (SELECT id FROM usuario WHERE username='joelma.gadelha');
UPDATE requisicao_tesouraria SET avaliado_por_id = (SELECT id FROM usuario WHERE username='joelma')
  WHERE avaliado_por_id = (SELECT id FROM usuario WHERE username='joelma.gadelha');
DELETE FROM usuario WHERE username = 'joelma.gadelha';

-- mariana.moura -> mariana
UPDATE usuario dest SET eh_aluno = TRUE, aluno_id = src.aluno_id, email = COALESCE(dest.email, src.email)
  FROM usuario src WHERE dest.username = 'mariana' AND src.username = 'mariana.moura';
UPDATE requisicao_tesouraria SET solicitante_id  = (SELECT id FROM usuario WHERE username='mariana')
  WHERE solicitante_id  = (SELECT id FROM usuario WHERE username='mariana.moura');
UPDATE requisicao_tesouraria SET avaliado_por_id = (SELECT id FROM usuario WHERE username='mariana')
  WHERE avaliado_por_id = (SELECT id FROM usuario WHERE username='mariana.moura');
DELETE FROM usuario WHERE username = 'mariana.moura';

-- matheus.lima -> matheus
UPDATE usuario dest SET eh_aluno = TRUE, aluno_id = src.aluno_id, email = COALESCE(dest.email, src.email)
  FROM usuario src WHERE dest.username = 'matheus' AND src.username = 'matheus.lima';
UPDATE requisicao_tesouraria SET solicitante_id  = (SELECT id FROM usuario WHERE username='matheus')
  WHERE solicitante_id  = (SELECT id FROM usuario WHERE username='matheus.lima');
UPDATE requisicao_tesouraria SET avaliado_por_id = (SELECT id FROM usuario WHERE username='matheus')
  WHERE avaliado_por_id = (SELECT id FROM usuario WHERE username='matheus.lima');
DELETE FROM usuario WHERE username = 'matheus.lima';

COMMIT;
```

**Confira depois** (as 4 contas `nome` devem ter `eh_aluno=t` + `aluno_id`; as `nome.sobrenome` somem):
```sql
SELECT username, eh_professor, eh_aluno, aluno_id FROM usuario
WHERE username IN ('jaqueline','joelma','mariana','matheus') ORDER BY username;
```

> No próximo boot, o backfill (`garantirAcessoParaTodos`) vê que o aluno já está vinculado à conta
> que ficou e **não recria** o `nome.sobrenome`. Sem efeitos colaterais.

---

## 2. Excluir `tes` e `lid`

Eles não excluem hoje porque têm **requisições de tesouraria** (FK): `tes` **avaliou** e `lid`
**abriu** requisições. Duas formas:

### a) Direto por SQL agora (independe de deploy)
```sql
-- confira o que cada um tem:
SELECT u.username,
       (SELECT count(*) FROM requisicao_tesouraria r WHERE r.solicitante_id  = u.id) AS abriu,
       (SELECT count(*) FROM requisicao_tesouraria r WHERE r.avaliado_por_id = u.id) AS avaliou
FROM usuario u WHERE u.username IN ('tes','lid');
```

```sql
BEGIN;
-- tes (só avaliou): desvincula o avaliador e exclui a conta (a requisição é preservada)
UPDATE requisicao_tesouraria SET avaliado_por_id = NULL
  WHERE avaliado_por_id = (SELECT id FROM usuario WHERE username='tes');
DELETE FROM usuario WHERE username = 'tes';

-- lid (abriu requisições): ⚠️ isto APAGA as requisições dele (e os anexos por cascade).
-- Só faça se forem dados de TESTE. Se forem reais, reatribua o solicitante em vez de apagar.
DELETE FROM requisicao_tesouraria WHERE solicitante_id = (SELECT id FROM usuario WHERE username='lid');
DELETE FROM usuario WHERE username = 'lid';
COMMIT;
```

### b) Pela tela (depois do deploy do fix — PR desta branch)
- **`tes`** passa a excluir normalmente pela tela de Usuários (a migration **V22** faz o avaliador
  virar `NULL` ao excluir, preservando a requisição).
- **`lid`** vai mostrar um **erro claro**: *"…abriu N requisição(ões)… reatribua ou exclua antes"*.
  Trate as requisições dele (item a) e então exclua pela tela.

---

## Por que o erro acontecia (corrigido no código)

`requisicao_tesouraria.solicitante_id` e `avaliado_por_id` referenciavam `usuario` **sem
`ON DELETE`**, então excluir quem abriu/avaliou requisições estourava a FK no banco (erro
genérico na tela). A migration **V22** deixa `avaliado_por` como `ON DELETE SET NULL`, e o
`UsuarioService.deletar` passou a devolver **409 com mensagem clara** quando o usuário é
solicitante de alguma requisição (o solicitante é obrigatório e não pode ficar órfão).
