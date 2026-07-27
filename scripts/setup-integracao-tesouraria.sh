#!/usr/bin/env bash
# ============================================================================
# setup-integracao-tesouraria.sh
#
# Configura, de uma vez, o acesso SOMENTE-LEITURA do sistema do tesoureiro às
# requisições, pelo Caminho A (túnel SSH) do docs/integracao-tesouraria.md:
#
#   1) BANCO  — cria/atualiza a view vw_requisicoes_integracao e o usuário
#               read-only "tesouraria_ro" (least privilege), com uma senha
#               gerada na hora (openssl) e exibida UMA vez.
#   2) SSH    — cria um usuário de SO só-túnel ("tunel_tesouraria") com uma
#               chave restrita (restrict + permitopen só para o Postgres),
#               sem shell — só serve para `ssh -N -L`.
#
# RODE ISTO NA VM ebd-db (onde ficam o docker-compose.db.yml e o Postgres).
# Precisa de sudo (usuário de SO/SSH) e de acesso ao Docker (banco).
#
# NÃO versiona senha nenhuma. A senha do banco é impressa uma vez, no fim;
# guarde-a no seu gerenciador e entregue ao tesoureiro por canal seguro.
#
# Uso:
#   sudo ./setup-integracao-tesouraria.sh --pubkey-file ./tesoureiro.pub
#   sudo ./setup-integracao-tesouraria.sh --pubkey "ssh-ed25519 AAAA... tesoureiro"
#   sudo ./setup-integracao-tesouraria.sh --db-only         # só o banco
#   sudo ./setup-integracao-tesouraria.sh --ssh-only --pubkey-file ./t.pub
#
# Flags (todas opcionais, com defaults sensatos):
#   --pubkey <str>        chave PÚBLICA do tesoureiro (ele gera; nós só recebemos a pública)
#   --pubkey-file <path>  arquivo com a chave pública
#   --tunnel-user <nome>  usuário de SO só-túnel        (default: tunel_tesouraria)
#   --db-role <nome>      papel read-only no Postgres   (default: tesouraria_ro)
#   --db-host <ip>        IP privado do Postgres        (default: 10.0.1.54)
#   --db-port <porta>     porta do Postgres             (default: 5432)
#   --db-name <nome>      banco                          (default: ebd)
#   --db-admin <nome>     superusuário do Postgres      (default: ebd)
#   --compose-dir <path>  pasta do docker-compose.db.yml (default: ~ebd-db ou .)
#   --db-only | --ssh-only
#   -h | --help
# ============================================================================
set -euo pipefail

# ---- defaults --------------------------------------------------------------
PUBKEY=""
PUBKEY_FILE=""
TUNNEL_USER="tunel_tesouraria"
DB_ROLE="tesouraria_ro"
DB_HOST="10.0.1.54"
DB_PORT="5432"
DB_NAME="ebd"
DB_ADMIN="ebd"
COMPOSE_DIR=""
DO_DB=1
DO_SSH=1

err()  { echo "ERRO: $*" >&2; exit 1; }
info() { echo -e "\033[1;36m›\033[0m $*"; }
ok()   { echo -e "\033[1;32m✓\033[0m $*"; }
warn() { echo -e "\033[1;33m!\033[0m $*"; }

# ---- parse -----------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --pubkey)       PUBKEY="${2:-}"; shift 2;;
    --pubkey-file)  PUBKEY_FILE="${2:-}"; shift 2;;
    --tunnel-user)  TUNNEL_USER="${2:-}"; shift 2;;
    --db-role)      DB_ROLE="${2:-}"; shift 2;;
    --db-host)      DB_HOST="${2:-}"; shift 2;;
    --db-port)      DB_PORT="${2:-}"; shift 2;;
    --db-name)      DB_NAME="${2:-}"; shift 2;;
    --db-admin)     DB_ADMIN="${2:-}"; shift 2;;
    --compose-dir)  COMPOSE_DIR="${2:-}"; shift 2;;
    --db-only)      DO_SSH=0; shift;;
    --ssh-only)     DO_DB=0; shift;;
    -h|--help)      sed -n '2,40p' "$0"; exit 0;;
    *) err "flag desconhecida: $1 (use --help)";;
  esac
done

# nome de papel/usuário simples, evita injeção de identificador
[[ "$DB_ROLE"     =~ ^[a-z_][a-z0-9_]*$ ]] || err "--db-role inválido: $DB_ROLE"
[[ "$TUNNEL_USER" =~ ^[a-z_][a-z0-9_-]*$ ]] || err "--tunnel-user inválido: $TUNNEL_USER"

# ============================================================================
# FASE 1 — BANCO (view + usuário read-only)
# ============================================================================
setup_db() {
  command -v docker >/dev/null || err "docker não encontrado — rode esta fase na ebd-db."

  # acha a pasta do compose
  if [[ -z "$COMPOSE_DIR" ]]; then
    for d in "$HOME/ebd-db" "$(eval echo ~ebd-db 2>/dev/null || true)" "." ; do
      [[ -n "$d" && -f "$d/docker-compose.db.yml" ]] && COMPOSE_DIR="$d" && break
    done
  fi
  [[ -n "$COMPOSE_DIR" && -f "$COMPOSE_DIR/docker-compose.db.yml" ]] \
    || err "não achei docker-compose.db.yml — passe --compose-dir <path>"
  info "compose em: $COMPOSE_DIR"

  local dc=(docker compose -f "$COMPOSE_DIR/docker-compose.db.yml")
  "${dc[@]}" ps db >/dev/null 2>&1 || err "container 'db' não está de pé nesse compose."

  # senha forte gerada agora (base64: sem aspas simples → seguro em literal SQL)
  local PGPASS
  PGPASS="$(openssl rand -base64 24)"

  info "aplicando view e (re)configurando o papel $DB_ROLE ..."
  # -T: sem TTY;  ON_ERROR_STOP: aborta no 1º erro
  "${dc[@]}" exec -T db \
    sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -v ON_ERROR_STOP=1 -U '"$DB_ADMIN"' -d '"$DB_NAME" <<SQL
-- View de integração (fonte da verdade: migration V17). Idempotente.
CREATE OR REPLACE VIEW vw_requisicoes_integracao AS
SELECT
    r.id AS requisicao_id, r.numero, r.status, r.ministerio, r.nome_evento,
    r.destinacao, r.motivo, r.valor_solicitado, r.valor_aprovado, r.valor_gasto,
    r.data_necessidade,
    COALESCE(sa.nome, su.username) AS solicitante,
    su.email AS solicitante_email,
    COALESCE(aa.nome, au.username) AS avaliado_por,
    r.avaliado_em, r.parecer_tesoureiro, r.observacao_final, r.finalizado_em, r.criado_em,
    (SELECT COUNT(*) FROM requisicao_anexo x WHERE x.requisicao_id = r.id) AS qtd_anexos,
    EXISTS (SELECT 1 FROM requisicao_anexo x WHERE x.requisicao_id = r.id) AS possui_nota_fiscal
FROM requisicao_tesouraria r
JOIN      usuario su ON su.id = r.solicitante_id
LEFT JOIN aluno   sa ON sa.id = su.aluno_id
LEFT JOIN usuario au ON au.id = r.avaliado_por_id
LEFT JOIN aluno   aa ON aa.id = au.aluno_id;

-- Papel read-only (cria se não existir; sempre reaplica senha e privilégio mínimo).
DO \$\$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${DB_ROLE}') THEN
    CREATE ROLE ${DB_ROLE} LOGIN CONNECTION LIMIT 5;
  END IF;
END \$\$;
ALTER ROLE ${DB_ROLE} PASSWORD '${PGPASS}';
REVOKE ALL     ON DATABASE ${DB_NAME} FROM ${DB_ROLE};
GRANT  CONNECT ON DATABASE ${DB_NAME} TO   ${DB_ROLE};
GRANT  USAGE   ON SCHEMA   public     TO   ${DB_ROLE};
REVOKE CREATE  ON SCHEMA   public     FROM ${DB_ROLE};
GRANT  SELECT  ON vw_requisicoes_integracao TO ${DB_ROLE};
SQL

  ok "banco pronto — papel $DB_ROLE lê só a view vw_requisicoes_integracao."
  # devolve a senha para o resumo final (variável global)
  GENERATED_PASS="$PGPASS"
}

# ============================================================================
# FASE 2 — SSH (usuário só-túnel com chave restrita)
# ============================================================================
setup_ssh() {
  [[ $EUID -eq 0 ]] || err "a fase SSH precisa de root — rode com sudo."

  # obtém a chave pública
  if [[ -z "$PUBKEY" && -n "$PUBKEY_FILE" ]]; then
    [[ -f "$PUBKEY_FILE" ]] || err "arquivo de chave não encontrado: $PUBKEY_FILE"
    PUBKEY="$(tr -d '\n' < "$PUBKEY_FILE")"
  fi
  [[ -n "$PUBKEY" ]] || err "informe a chave pública do tesoureiro (--pubkey ou --pubkey-file)."
  [[ "$PUBKEY" =~ ^(ssh-ed25519|ssh-rsa|ecdsa-sha2-|sk-ssh-) ]] \
    || err "isso não parece uma chave PÚBLICA SSH válida."

  # cria o usuário só-túnel (nologin: sem shell; -N no cliente não precisa de shell)
  if ! id "$TUNNEL_USER" >/dev/null 2>&1; then
    useradd -m -s /usr/sbin/nologin "$TUNNEL_USER"
    ok "usuário de SO '$TUNNEL_USER' criado (nologin)."
  else
    info "usuário '$TUNNEL_USER' já existe — reaproveitando."
  fi

  local home ssh_dir ak
  home="$(getent passwd "$TUNNEL_USER" | cut -d: -f6)"
  ssh_dir="$home/.ssh"; ak="$ssh_dir/authorized_keys"
  install -d -m 700 -o "$TUNNEL_USER" -g "$TUNNEL_USER" "$ssh_dir"
  touch "$ak"; chmod 600 "$ak"; chown "$TUNNEL_USER:$TUNNEL_USER" "$ak"

  # linha restrita: 'restrict' desliga tudo; reabilita só o forward, preso ao Postgres
  local opts="restrict,port-forwarding,permitopen=\"${DB_HOST}:${DB_PORT}\""
  local line="$opts $PUBKEY"

  # dedup: se a mesma chave já estiver lá, substitui a linha
  local keybody; keybody="$(awk '{print $2}' <<<"$PUBKEY")"
  if [[ -n "$keybody" ]] && grep -qF "$keybody" "$ak" 2>/dev/null; then
    grep -vF "$keybody" "$ak" > "$ak.tmp" && mv "$ak.tmp" "$ak"
    chmod 600 "$ak"; chown "$TUNNEL_USER:$TUNNEL_USER" "$ak"
    info "chave já existia — atualizando as restrições."
  fi
  printf '%s\n' "$line" >> "$ak"
  ok "chave restrita instalada para '$TUNNEL_USER' (permitopen ${DB_HOST}:${DB_PORT})."

  # sanidade do sshd: forwarding precisa estar ligado (é o default)
  if sshd -T 2>/dev/null | grep -qi '^allowtcpforwarding no'; then
    warn "sshd está com AllowTcpForwarding=no — o túnel NÃO vai funcionar."
    warn "ligue em /etc/ssh/sshd_config (AllowTcpForwarding yes) e 'systemctl reload ssh'."
  fi
}

# ============================================================================
GENERATED_PASS=""
[[ $DO_DB  -eq 1 ]] && setup_db
[[ $DO_SSH -eq 1 ]] && setup_ssh

# ---- resumo / próximos passos ---------------------------------------------
echo
echo "=================================================================="
ok "Configuração concluída."
echo "=================================================================="
if [[ $DO_DB -eq 1 ]]; then
  echo
  warn "SENHA do usuário de banco '$DB_ROLE' (aparece só AGORA — guarde já):"
  echo
  echo "      $GENERATED_PASS"
  echo
  echo "  Guarde no gerenciador de senhas e entregue ao tesoureiro por canal seguro."
  echo "  Para rotacionar depois: rode este script de novo com --db-only."
fi
cat <<TXT

Como o tesoureiro conecta (Caminho A — túnel SSH):

  1) Na máquina dele, abrir o túnel (mantém aberto):
       ssh -N -L 5433:${DB_HOST}:${DB_PORT} ${TUNNEL_USER}@<IP_PUBLICO_DA_ebd-db>

  2) Apontar o sistema/consulta para:
       host=localhost  port=5433  dbname=${DB_NAME}  user=${DB_ROLE}
       (a senha é a mostrada acima; o túnel SSH já cifra o tráfego)

  3) Teste rápido:
       psql "host=localhost port=5433 dbname=${DB_NAME} user=${DB_ROLE}" \\
         -c "SELECT numero,status,valor_aprovado FROM vw_requisicoes_integracao LIMIT 5;"

FALTA (passo manual, no console da OCI):
  • Liberar a porta 22 da ebd-db para o IP público FIXO do tesoureiro
    (Security List da subnet → Ingress: <IP>/32 TCP 22). A 5432 continua FECHADA.

Detalhes e o Caminho B (exposição direta) em docs/integracao-tesouraria.md
TXT
