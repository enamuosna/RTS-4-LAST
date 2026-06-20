#!/bin/bash
# =============================================================
#  RTS Caisse - Deploiement / mise a jour PRODUCTION (Ubuntu)
# =============================================================
#  A executer SUR LE SERVEUR. Recupere la derniere version du
#  code, reconstruit backend + frontend, relance toute la stack
#  (docker-compose.prod.yml + Caddy auto-HTTPS) puis verifie la
#  sante du backend et le seed des donnees (langues V15).
#
#  Usage :
#    cd rts-caisse-docker
#    ./scripts/deploy-prod.sh                 # branche courante
#    ./scripts/deploy-prod.sh main            # force une branche
#
#  Prerequis : .env present dans rts-caisse-docker/ avec au moins
#    POSTGRES_PASSWORD, JWT_SECRET, SERVER_NAME, LETSENCRYPT_EMAIL
#  (cf. env.example). Le depot complet (backend + web + docker)
#  doit etre cote a cote car le build se fait depuis les sources.
# =============================================================

set -euo pipefail

# --- 0. Se placer dans rts-caisse-docker/ quel que soit l'appel ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${DOCKER_DIR}/.." && pwd)"
COMPOSE="docker compose -f ${DOCKER_DIR}/docker-compose.prod.yml"

cd "${DOCKER_DIR}"

if [ ! -f ".env" ]; then
    echo "[ERROR] .env introuvable dans ${DOCKER_DIR}. Copier env.example et le renseigner." >&2
    exit 1
fi

# Charge .env (POSTGRES_USER / POSTGRES_DB pour les verifications psql)
set -a; # shellcheck disable=SC1091
source .env; set +a
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-rts_caisse_db}"

# --- 1. Recuperer la derniere version du code ---
cd "${REPO_ROOT}"
BRANCH="${1:-$(git rev-parse --abbrev-ref HEAD)}"
echo "============================================================"
echo " RTS Caisse - Deploiement PROD (branche : ${BRANCH})"
echo "============================================================"
echo "[1/5] Recuperation du code (git fetch + reset --hard)..."
git fetch origin "${BRANCH}"
# reset --hard : deterministe malgre les artefacts compiles suivis par git.
# N'affecte PAS les fichiers non suivis (le .env est preserve).
git reset --hard "origin/${BRANCH}"
echo "      HEAD = $(git rev-parse --short HEAD) - $(git log -1 --pretty=%s)"

# --- 2. Rebuild + redemarrage de TOUTE la stack ---
# IMPORTANT : pas d'argument de service => Caddy/Frontend ne sont pas
# laisses a l'arret (piege connu des rebuilds service par service).
echo "[2/5] Build des images + redemarrage de la stack..."
cd "${DOCKER_DIR}"
${COMPOSE} up -d --build

# --- 3. Attente du backend "healthy" ---
echo "[3/5] Attente du backend (healthy)..."
DEADLINE=$(( $(date +%s) + 180 ))
while true; do
    STATUS="$(docker inspect -f '{{.State.Health.Status}}' rts-caisse-backend 2>/dev/null || echo absent)"
    if [ "${STATUS}" = "healthy" ]; then
        echo "      backend : healthy"
        break
    fi
    if [ "$(date +%s)" -ge "${DEADLINE}" ]; then
        echo "[ERROR] backend toujours '${STATUS}' apres 180s. Logs :" >&2
        docker logs rts-caisse-backend --tail 40 >&2 || true
        exit 1
    fi
    sleep 5
done

# --- 4. Verifications schema + donnees (feature diffusions/langues) ---
echo "[4/5] Verifications base de donnees..."
psql_q() { docker exec rts-caisse-postgres psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -tAc "$1" 2>/dev/null | tr -d '[:space:]'; }

NB_LANGUES="$(psql_q "select count(*) from langues")"
HAS_DIFF="$(psql_q "select to_regclass('public.operation_diffusion') is not null")"
HAS_FLAG="$(psql_q "select count(*) from information_schema.columns where table_name='categories_operation' and column_name='propose_langue'")"

echo "      langues seedees        : ${NB_LANGUES:-0}"
echo "      table operation_diffusion : ${HAS_DIFF:-?}"
echo "      colonne propose_langue : ${HAS_FLAG:-0}"

if [ "${NB_LANGUES:-0}" -lt 1 ] || [ "${HAS_DIFF:-f}" != "t" ] || [ "${HAS_FLAG:-0}" -lt 1 ]; then
    echo "[ERROR] Schema/donnees incomplets pour la feature diffusions/langues." >&2
    echo "        Verifier les logs backend (DataInitializer / auto-reparation schema)." >&2
    exit 1
fi

# --- 5. Recapitulatif ---
echo "[5/5] Etat des conteneurs :"
${COMPOSE} ps
echo "============================================================"
echo " DEPLOIEMENT TERMINE - https://${SERVER_NAME:-<SERVER_NAME>}"
echo "============================================================"
