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

## Provas e notas

| Método | Rota | Perfil |
|---|---|---|
| GET | `/provas` · `/provas/{id}` | A, P |
| POST · PUT · DELETE | `/provas` · `/provas/{id}` | A |
| GET | `/provas/{id}/notas` | A, P |
| PUT | `/provas/{id}/notas` | A, P |

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
