# Unificação de papéis — um usuário, vários papéis

Antes, ADMIN/PROFESSOR/ALUNO eram uma **role base única** por login. Agora são **capacidades
(flags)** — `eh_admin`, `eh_professor`, `eh_aluno` (+ `eh_tesoureiro`, `eh_lider`) — que qualquer
usuário acumula (migration **V19**). Assim uma pessoa que é professor **e** aluno usa **um login só**.

## O que mudou

- `usuario.role` deixou de existir; entraram as flags `eh_*`. O JWT (`TokenService`) emite **um
  grupo por flag**, então os `@RolesAllowed` (`ADMIN`/`PROFESSOR`/`ALUNO`/`TESOUREIRO`/`LIDER`) e o
  `EscopoService` continuam iguais. O **ADMIN** recebe TESOUREIRO/LIDER por padrão.
- Front: a tela de **Usuários** atribui papéis por **checkboxes**; um **alternador "Atuando como"**
  (Professor/Administração ↔ Aluno) aparece no topo para quem tem mais de um perfil e troca só o
  foco do menu (o backend não tem "modo" — o token carrega todos os grupos).
- A regra "professor que dá a aula não conta como aluno" segue **por aula** (o `aula.professor.aluno`
  é excluído só naquela aula). Nas aulas em que ele não está escalado, participa como aluno normal.

## Consolidar contas duplicadas (professor + aluno)

A migração V19 converte `role → flag` **1:1**, sem perda: quem tinha 2 contas (uma PROFESSOR feita à
mão + uma ALUNO criada automaticamente pelo `AcessoAlunoService`) **continua com as 2**. Para
unificar cada pessoa em um login só, o admin faz **manualmente**, na tela de **Usuários**:

1. **Edite a conta de PROFESSOR** (a que aparece como professor das aulas — `aula.professor_id`
   aponta pra ela). Marque também o papel **Aluno** e, em **Aluno vinculado**, selecione o registro
   de aluno da pessoa (o mesmo aluno da conta automática).
2. **Exclua a conta ALUNO automática** redundante daquela pessoa (a que tinha aquele aluno vinculado).
   Como as aulas já apontam para a conta que ficou, **não há nada a reapontar**.
3. Pronto. No próximo boot, o backfill (`garantirAcessoParaTodos`) vê que o aluno já está vinculado
   à conta que ficou e **não recria** o login automático.

> Faça isso com o sistema tranquilo e confira depois que a pessoa consegue logar e ver o alternador
> "Atuando como". Como a senha da conta de professor é preservada, ela continua entrando com a mesma.

## Observações

- **Não** há `UNIQUE(aluno_id)` no banco ainda — de propósito, porque durante a consolidação duas
  contas podem apontar para o mesmo aluno por um instante. Depois de consolidar tudo, dá pra
  endurecer com uma migration adicionando a constraint (hardening opcional).
- Criar um **professor que não é aluno** (ex.: um convidado): crie o usuário só com o papel
  Professor, sem aluno vinculado. Criar um **aluno**: o login já é criado automaticamente; para
  torná-lo também professor, edite-o e marque Professor + turmas.
