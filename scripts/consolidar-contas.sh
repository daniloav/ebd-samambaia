#!/usr/bin/env bash
#
# consolidar-contas.sh — consolida contas duplicadas (x.sobrenome -> x) e exclui
# usuarios de teste (tes/lid) que hoje travam por causa das requisicoes de tesouraria.
#
# Roda contra o Postgres de PRODUCAO (ebd-db). Por padrao e' DRY-RUN: mostra o
# estado atual e o SQL que seria executado, sem alterar nada. Use --executar para
# aplicar. A exclusao das requisicoes do 'lid' (destrutiva) exige a flag propria.
#
# Uso (na VM ebd-db, em ~/ebd-db):
#   ./consolidar-contas.sh                          # dry-run (nao altera nada)
#   ./consolidar-contas.sh --executar               # consolida 4 pares + exclui 'tes'
#   ./consolidar-contas.sh --executar --apagar-requisicoes-lid   # + apaga reqs do 'lid' e exclui 'lid'
#
# Conexao: por padrao usa o compose da ebd-db. Sobrescreva com EBD_PSQL, ex.:
#   EBD_PSQL='psql "postgres://ebd:ebd@localhost:5432/ebd"' ./consolidar-contas.sh
#
# ⚠️ FACA BACKUP ANTES (pg_dump). Veja docs/consolidacao-contas.md.

set -euo pipefail

EXECUTAR=0
APAGAR_LID=0
for arg in "$@"; do
  case "$arg" in
    --executar) EXECUTAR=1 ;;
    --apagar-requisicoes-lid) APAGAR_LID=1 ;;
    -h|--help) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Opcao desconhecida: $arg (use --help)"; exit 2 ;;
  esac
done

# Comando psql. Default: compose da ebd-db (rode em ~/ebd-db). ON_ERROR_STOP no psql_run.
EBD_PSQL=${EBD_PSQL:-"docker compose -f docker-compose.db.yml exec -T db psql -U ebd -d ebd"}

# Executa SQL vindo do stdin, abortando em qualquer erro.
psql_run() { $EBD_PSQL -v ON_ERROR_STOP=1 -q "$@"; }

# Pares de consolidacao: "origem destino" (origem = conta auto de aluno; destino = conta a manter).
PARES=(
  "jaqueline.costa jaqueline"
  "joelma.gadelha  joelma"
  "mariana.moura   mariana"
  "matheus.lima    matheus"
)

# --- Gera o SQL da consolidacao + exclusao do 'tes' (parte segura, transacional) ---
gerar_sql_seguro() {
  echo "BEGIN;"
  for par in "${PARES[@]}"; do
    read -r src dest <<<"$par"
    cat <<SQL

-- ${src} -> ${dest}
UPDATE usuario dest SET eh_aluno = TRUE, aluno_id = src.aluno_id,
       email = COALESCE(dest.email, src.email)
  FROM usuario src WHERE dest.username = '${dest}' AND src.username = '${src}';
UPDATE requisicao_tesouraria SET solicitante_id  = (SELECT id FROM usuario WHERE username='${dest}')
  WHERE solicitante_id  = (SELECT id FROM usuario WHERE username='${src}');
UPDATE requisicao_tesouraria SET avaliado_por_id = (SELECT id FROM usuario WHERE username='${dest}')
  WHERE avaliado_por_id = (SELECT id FROM usuario WHERE username='${src}');
DELETE FROM usuario WHERE username = '${src}';
SQL
  done
  cat <<'SQL'

-- 'tes' (so avaliou): desvincula o avaliador (requisicao preservada) e exclui a conta.
UPDATE requisicao_tesouraria SET avaliado_por_id = NULL
  WHERE avaliado_por_id = (SELECT id FROM usuario WHERE username='tes');
DELETE FROM usuario WHERE username = 'tes';
COMMIT;
SQL
}

# --- Gera o SQL destrutivo do 'lid' (apaga as requisicoes dele + a conta) ---
gerar_sql_lid() {
  cat <<'SQL'
BEGIN;
-- ⚠️ DESTRUTIVO: apaga as requisicoes abertas por 'lid' (e anexos por cascade).
DELETE FROM requisicao_tesouraria WHERE solicitante_id = (SELECT id FROM usuario WHERE username='lid');
DELETE FROM usuario WHERE username = 'lid';
COMMIT;
SQL
}

echo "=============================================================="
echo " Estado ANTES"
echo "=============================================================="
psql_run <<'SQL'
\echo -- Contas dos 4 pares (destino deve ficar com eh_aluno + aluno_id):
SELECT username, eh_admin, eh_professor, eh_aluno, aluno_id FROM usuario
 WHERE username IN ('jaqueline','jaqueline.costa','joelma','joelma.gadelha',
                    'mariana','mariana.moura','matheus','matheus.lima')
 ORDER BY username;
\echo
\echo -- tes/lid e quantas requisicoes cada um tem:
SELECT u.username,
       (SELECT count(*) FROM requisicao_tesouraria r WHERE r.solicitante_id  = u.id) AS abriu,
       (SELECT count(*) FROM requisicao_tesouraria r WHERE r.avaliado_por_id = u.id) AS avaliou
  FROM usuario u WHERE u.username IN ('tes','lid') ORDER BY u.username;
SQL

if [[ "$EXECUTAR" -eq 0 ]]; then
  echo
  echo "=============================================================="
  echo " DRY-RUN — nada foi alterado. SQL que seria executado:"
  echo "=============================================================="
  echo "--- consolidacao dos 4 pares + exclusao de 'tes' (seguro) ---"
  gerar_sql_seguro
  echo
  echo "--- exclusao de 'lid' (SO com --apagar-requisicoes-lid; DESTRUTIVO) ---"
  gerar_sql_lid
  echo
  echo ">> Reveja o estado acima. Para aplicar: ./consolidar-contas.sh --executar"
  echo ">> Para tambem apagar as requisicoes do 'lid': adicione --apagar-requisicoes-lid"
  exit 0
fi

echo
echo "Aplicando consolidacao dos 4 pares + exclusao de 'tes'..."
gerar_sql_seguro | psql_run
echo "OK — pares consolidados e 'tes' excluido."

if [[ "$APAGAR_LID" -eq 1 ]]; then
  echo
  read -r -p "⚠️  Isto APAGA as requisicoes do 'lid' (registros financeiros). Digite 'APAGAR' para confirmar: " conf
  if [[ "$conf" == "APAGAR" ]]; then
    gerar_sql_lid | psql_run
    echo "OK — requisicoes do 'lid' apagadas e conta excluida."
  else
    echo "Cancelado o passo do 'lid' (confirmacao nao foi 'APAGAR'). Nada apagado do 'lid'."
  fi
else
  echo
  echo "NOTA: 'lid' NAO foi excluido (abriu requisicoes). Se forem dados de teste,"
  echo "      rode de novo com --apagar-requisicoes-lid. Se forem reais, reatribua"
  echo "      o solicitante antes (veja docs/consolidacao-contas.md)."
fi

echo
echo "=============================================================="
echo " Estado DEPOIS"
echo "=============================================================="
psql_run <<'SQL'
SELECT username, eh_professor, eh_aluno, aluno_id FROM usuario
 WHERE username IN ('jaqueline','joelma','mariana','matheus') ORDER BY username;
SELECT username FROM usuario WHERE username IN ('tes','lid',
       'jaqueline.costa','joelma.gadelha','mariana.moura','matheus.lima') ORDER BY username;
SQL
echo "Concluido."
