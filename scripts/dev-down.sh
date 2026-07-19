#!/usr/bin/env bash
# Para o ambiente de desenvolvimento local (backend + frontend).
# O PostgreSQL continua rodando (é serviço do Homebrew).
# Uso:  ./scripts/dev-down.sh
set -uo pipefail

parar_porta(){
  local porta="$1" nome="$2"
  local pids
  pids=$(lsof -nP -iTCP:"$porta" -sTCP:LISTEN -t 2>/dev/null || true)
  if [ -n "$pids" ]; then
    kill $pids 2>/dev/null || true
    echo "🛑 $nome parado (porta $porta, PID $pids)"
  else
    echo "· $nome não estava rodando (porta $porta)"
  fi
}

parar_porta 8080 "Backend"
parar_porta 4200 "Frontend"

# garante que o mvn/ng que respawnam também morram
pkill -f "quarkus:dev" 2>/dev/null || true
pkill -f "ng serve"    2>/dev/null || true

echo
echo "PostgreSQL segue no ar (serviço). Para pará-lo também:"
echo "  brew services stop postgresql@16"
