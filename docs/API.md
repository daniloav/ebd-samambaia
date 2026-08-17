# Referência da API — EBD Adultos

Base URL: `http://localhost:8080/api` (dev) · `/api` (produção, atrás do nginx).
Documentação interativa: **Swagger UI** em `/q/swagger-ui`.

Autenticação: envie `Authorization: Bearer <token>` (obtido no login) em todas as rotas,
exceto `POST /auth/login`. Perfis: **A** = ADMIN, **P** = PROFESSOR.

## Autenticação

### POST `/auth/login` — público
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","senha":"admin123"}'
```
```json
{ "token": "eyJ...", "username": "admin", "role": "ADMIN", "expiresInSeconds": 28800 }
```

### GET `/me` — A, P
Retorna `{ "username": "...", "roles": ["ADMIN"] }` do usuário do token.

## Alunos

| Método | Rota | Perfil | Descrição |
|---|---|---|---|
| GET | `/alunos?apenasAtivos=false` | A, P | Lista alunos (ordem por nome) |
| GET | `/alunos/{id}` | A, P | Busca um aluno |
| POST | `/alunos` | A | Cria aluno |
| PUT | `/alunos/{id}` | A | Atualiza aluno |
| DELETE | `/alunos/{id}` | A | Exclui (cascata em presenças/notas) |

Corpo (POST/PUT):
```json
{ "nome": "Ana Souza", "telefone": "(61) 90000-0000", "dataNascimento": "1990-05-12", "ativo": true }
```
Só `nome` é obrigatório.

## Aulas

| Método | Rota | Perfil |
|---|---|---|
| GET | `/aulas` | A, P |
| GET | `/aulas/{id}` | A, P |
| POST | `/aulas` | A, P |
| PUT | `/aulas/{id}` | A, P |
| DELETE | `/aulas/{id}` | A |

Corpo: `{ "data": "2026-07-19", "tema": "A graça de Deus" }` (`data` obrigatória e única).

**Leituras bíblicas diárias** (opcional) — o campo `textos` entra no mesmo corpo do POST/PUT e é a
**íntegra** do cadastro: dia ausente (ou com `referencia` em branco) é removido; campo **nulo/ausente**
não mexe no que já existe. Trocar a referência zera o texto em cache e o carimbo de envio.

```json
{
  "classeId": 1, "data": "2026-08-23", "tema": "A graça de Deus",
  "textos": [
    { "diaSemana": "SEGUNDA", "referencia": "Sl 1.1-6" },
    { "diaSemana": "TERCA", "referencia": "1Jo 4.7-8" }
  ]
}
```

`diaSemana` ∈ `DOMINGO|SEGUNDA|TERCA|QUARTA|QUINTA|SEXTA|SABADO`. Na resposta cada leitura traz
`dataLeitura` — o dia em que ela é enviada, sempre na **semana que antecede** a aula (aula de
23/08 → segunda 17/08, sábado 22/08) — e `enviadoEm`.

## Chamada

### GET `/aulas/{aulaId}/chamada` — A, P
Retorna todos os alunos ativos com os itens já registrados (ou zerados):
```json
{
  "aulaId": 1, "data": "2026-07-19", "tema": "A graça de Deus",
  "itens": [
    { "alunoId": 3, "alunoNome": "Ana Souza",
      "presente": true, "trouxeBiblia": true, "trouxeRevista": false, "estudouLicao": true }
  ]
}
```

### PUT `/aulas/{aulaId}/chamada` — A, P
Salva (upsert) a chamada. Envie uma linha por aluno:
```json
{ "itens": [
  { "alunoId": 3, "presente": true, "trouxeBiblia": true, "trouxeRevista": false, "estudouLicao": true }
] }
```

## Relatório

### GET `/relatorios/presencas?inicio=2026-01-01&fim=2026-12-31` — A, P
Sem parâmetros = todo o histórico. Resposta:
```json
{
  "inicio": "2026-01-01", "fim": "2026-12-31", "totalAulas": 10,
  "itens": [
    { "alunoId": 3, "nome": "Ana Souza", "totalAulas": 10, "presencas": 8, "faltas": 2,
      "percentualPresenca": 80.0, "trouxeBiblia": 7, "trouxeRevista": 6, "estudouLicao": 5 }
  ]
}
```

### GET `/relatorios/inativados?inicio=&fim=&classeId=&incluirReativados=` — A, P
Alunos **inativados** (histórico `aluno_inativacao`): uma linha por episódio de saída. Sem
`inicio`/`fim` entram todos, inclusive os episódios **sem data** (histórico anterior à V30);
com período, só os datados — `semDataRegistrada` diz quantos ficaram de fora. `incluirReativados=true`
traz também quem já voltou (`reativadoEm` preenchido). Sem `classeId` = geral (**só ADMIN**).
```json
{
  "inicio": "2000-01-01", "fim": "2026-08-16", "periodoAberto": true,
  "classeId": null, "classeNome": null,
  "total": 2, "aindaInativos": 2, "reativados": 0,
  "porFaltasSeguidas": 1, "manuais": 0, "semDataRegistrada": 1,
  "itens": [
    { "alunoId": 21, "nome": "Fulano", "turma": "Adultos", "email": null, "telefone": "619...",
      "inativadoEm": "2026-07-17T23:36:28", "motivo": "FALTAS_SEGUIDAS", "faltasSeguidas": 5,
      "inativadoPor": "professor", "ultimaPresenca": "2026-06-07", "reativadoEm": null }
  ]
}
```

### GET `/relatorios/visitantes?inicio=&fim=&classeId=` — A, P
Visitantes do período. Sem `classeId` = geral (todas as turmas, **só ADMIN**); com `classeId` =
restrito à turma (professor precisa da turma no seu escopo). Resposta:
```json
{
  "inicio": "2026-01-01", "fim": "2026-12-31", "classeId": null, "classeNome": null, "total": 1,
  "itens": [
    { "id": 2, "nome": "Visitante Fulano", "email": "v@x.com", "telefone": "619...",
      "turma": "Adultos", "dataAula": "2026-08-23", "trazidoPorNome": "Ana Souza" }
  ]
}
```

## Provas e notas

| Método | Rota | Perfil |
|---|---|---|
| GET | `/provas` · `/provas/{id}` | A, P |
| POST · PUT · DELETE | `/provas` · `/provas/{id}` | A |
| GET | `/provas/{id}/notas` | A, P |
| PUT | `/provas/{id}/notas` | A, P |
| POST | `/provas/{id}/notas/notificar` | A, P |

Prova (POST/PUT): `{ "titulo": "Prova do 1º trimestre", "data": "2026-03-30", "notaMaxima": 10.0 }`

Grade de notas — GET retorna `nota: null` para quem ainda não tem nota:
```json
{ "provaId": 1, "titulo": "Prova do 1º trimestre", "data": "2026-03-30", "notaMaxima": 10.0,
  "itens": [ { "alunoId": 3, "alunoNome": "Ana Souza", "nota": 8.5 } ] }
```
Salvar notas (PUT) — `nota: null` remove a nota do aluno; nota acima da máxima → `400`:
```json
{ "itens": [ { "alunoId": 3, "nota": 8.5 } ] }
```
"Lançar e notificar" (POST `/provas/{id}/notas/notificar`) — envia por e-mail o desempenho a cada
aluno com nota lançada (com e-mail e opt-in). Resposta: `{ "enviados": 2 }`.

## Boletim

### GET `/boletim?alunoId=&ano=2026&trimestre=3` — A, P
### GET `/me/boletim?ano=2026&trimestre=3` — ALUNO (próprio)
Trimestre 1..4 (Jan-Mar, Abr-Jun, Jul-Set, Out-Dez). Resposta:
```json
{
  "alunoId": 3, "alunoNome": "Ana Souza", "turma": "Adultos",
  "ano": 2026, "trimestre": 3, "periodoInicio": "2026-07-01", "periodoFim": "2026-09-30",
  "provas": [ { "titulo": "Prova Lição 5", "data": "2026-08-23", "nota": 9.5, "notaMaxima": 10.0, "percentual": 95.0 } ],
  "mediaNotas": 9.5, "aproveitamentoPct": 95.0,
  "frequencia": { "totalAulas": 1, "presencas": 1, "faltas": 0, "percentualPresenca": 100.0,
                  "biblias": 1, "revistas": 1, "licoes": 1 },
  "visitantesTrazidos": 1, "situacao": "Aprovado"
}
```

### GET `/relatorios/mensal?ano=2026&mes=8&classeIds=1&classeIds=2` — A, P
Relatório geral de presença do mês, consolidando as turmas escolhidas. `mes` vazio = **ano inteiro**;
`classeIds` vazio = **todas as turmas** que o usuário pode ver (professor só gera das dele — turma fora
do escopo devolve 403). Aulas **adiadas** ficam de fora.

```json
{
  "ano": 2026, "mes": 8, "inicio": "2026-08-01", "fim": "2026-08-31",
  "periodoLabel": "Agosto de 2026",
  "turmas": [ { "classeId": 1, "classeNome": "Adultos" } ],
  "totais": { "aulas": 4, "aulasComChamada": 3, "alunosAtivos": 7, "presencas": 17, "faltas": 11,
              "faltasJustificadas": 2, "percentualPresenca": 60.71,
              "biblias": 16, "revistas": 3, "licoes": 4, "visitantes": 1 },
  "porTurma": [ { "classeId": 1, "classeNome": "Adultos", "totais": { "...": "mesma estrutura" } } ],
  "serie": [ { "rotulo": "02/08", "data": "2026-08-02",
               "totais": { "...": "" },
               "porTurma": [ { "classeId": 1, "classeNome": "Adultos",
                               "presencas": 5, "faltas": 2, "percentualPresenca": 71.43 } ] } ]
}
```

`percentualPresenca` = presenças ÷ (presenças + faltas), ou seja, a base são os registros da chamada.
`serie` alimenta o gráfico da tela: um ponto por **domingo** quando `mes` vem preenchido, um ponto por
**mês** quando o relatório é do ano.

## Administração

### POST `/admin/aniversarios/executar` — A
Dispara na hora o envio de parabéns dos aniversariantes de hoje (o mesmo do agendamento das 12:00 BRT).
Resposta: `{ "total": 1, "enviados": 1, "nomes": ["Ana Souza"] }`.

### POST `/admin/lembretes-chamada/executar` — A
Dispara na hora o lembrete das chamadas pendentes de hoje (o mesmo do agendamento de hora em hora,
12h–21h BRT). Resposta: `{ "aulasPendentes": 1, "enviados": 1, "turmas": ["Adultos"] }`.

### POST `/admin/leituras-diarias/executar` — A
Dispara na hora o envio das leituras bíblicas do dia (o mesmo do agendamento das 12:00 BRT).
Resposta: `{ "leituras": 1, "enviados": 2, "referencias": ["Sl 1.1-6"] }`.

## Desafios

### GET `/desafios/rankings` — A, P
Top 5 por categoria (derivado da chamada + notas):
```json
{
  "totalAulas": 10, "totalProvas": 2,
  "menosFaltou":        [ { "posicao": 1, "alunoId": 3, "nome": "Ana Souza", "valor": 10, "detalhe": "10 presença(s) de 10 aula(s)" } ],
  "maisTrouxeBiblia":   [ ... ],
  "maisTrouxeRevista":  [ ... ],
  "maisEstudouLicao":   [ ... ],
  "melhoresNotas":      [ { "posicao": 1, "alunoId": 3, "nome": "Ana Souza", "valor": 9.25, "detalhe": "média 9.25" } ]
}
```

## Erros

Respostas de erro seguem `{ "message": "...", "status": <código> }`. Códigos comuns:
`400` (validação/regra), `401` (sem token ou expirado), `403` (perfil sem permissão),
`404` (não encontrado), `409` (conflito, ex.: aula com data duplicada).
