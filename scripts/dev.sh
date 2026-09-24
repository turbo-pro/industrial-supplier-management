#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="${PROJECT_ROOT}/tmp/dev"
LOG_DIR="${PROJECT_ROOT}/logs/dev"
COMPOSE_FILE="${PROJECT_ROOT}/deploy/docker/compose.dev.yml"
ENV_FILE="${PROJECT_ROOT}/deploy/docker/.env"
ENV_EXAMPLE="${PROJECT_ROOT}/deploy/docker/.env.example"

mkdir -p "${RUNTIME_DIR}" "${LOG_DIR}"

usage() {
  echo "Usage: ./scripts/dev.sh {up|down|restart|status|logs}"
}

pid_file() { echo "${RUNTIME_DIR}/$1.pid"; }
log_file() { echo "${LOG_DIR}/$1.log"; }

is_running() {
  local file
  file="$(pid_file "$1")"
  [[ -f "${file}" ]] && kill -0 "$(cat "${file}")" 2>/dev/null
}

start_process() {
  local name="$1"
  shift
  if is_running "${name}"; then
    echo "[skip] ${name} is already running (pid $(cat "$(pid_file "${name}")"))"
    return
  fi
  rm -f "$(pid_file "${name}")"
  echo "[start] ${name}"
  (
    cd "${PROJECT_ROOT}"
    nohup "$@" >"$(log_file "${name}")" 2>&1 &
    echo $! >"$(pid_file "${name}")"
  )
}

stop_process() {
  local name="$1"
  local file
  file="$(pid_file "${name}")"
  if ! is_running "${name}"; then
    rm -f "${file}"
    echo "[skip] ${name} is not running"
    return
  fi
  local pid
  pid="$(cat "${file}")"
  echo "[stop] ${name} (pid ${pid})"
  while read -r child; do
    [[ -n "${child}" ]] && kill "${child}" 2>/dev/null || true
  done < <(pgrep -P "${pid}" 2>/dev/null || true)
  kill "${pid}" 2>/dev/null || true
  for _ in {1..30}; do
    if ! kill -0 "${pid}" 2>/dev/null; then
      rm -f "${file}"
      return
    fi
    sleep 1
  done
  echo "[warn] ${name} did not stop within 30 seconds; sending SIGKILL"
  kill -9 "${pid}" 2>/dev/null || true
  rm -f "${file}"
}

wait_for_frontend() {
  local name="$1"
  local url="$2"
  for _ in {1..60}; do
    if curl --silent --fail "${url}" >/dev/null 2>&1; then
      echo "[ready] ${name} ${url}"
      return
    fi
    if ! is_running "${name}"; then
      echo "[error] ${name} exited. Last log lines:"
      tail -n 30 "$(log_file "${name}")" || true
      exit 1
    fi
    sleep 1
  done
  echo "[error] ${name} did not become ready within 60 seconds"
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "[error] Missing command: $1"; exit 1; }
}

wait_for_backend() {
  echo "[wait] backend health check"
  for _ in {1..120}; do
    if curl --silent --fail http://127.0.0.1:18080/actuator/health >/dev/null 2>&1; then
      echo "[ready] backend http://127.0.0.1:18080"
      return
    fi
    if ! is_running backend; then
      echo "[error] backend exited. Last log lines:"
      tail -n 40 "$(log_file backend)" || true
      exit 1
    fi
    sleep 1
  done
  echo "[error] backend did not become healthy within 120 seconds"
  tail -n 40 "$(log_file backend)" || true
  exit 1
}

up() {
  require_command docker
  require_command curl
  require_command corepack
  require_command pgrep
  require_command java
  docker info >/dev/null 2>&1 || { echo "[error] Docker Engine is unavailable. Start Docker Desktop first."; exit 1; }
  if [[ ! -f "${ENV_FILE}" ]]; then
    cp "${ENV_EXAMPLE}" "${ENV_FILE}"
    echo "[create] deploy/docker/.env"
  fi
  echo "[start] MySQL"
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --wait
  if [[ ! -d "${PROJECT_ROOT}/node_modules" ]]; then
    echo "[install] frontend dependencies"
    corepack pnpm install
  fi
  echo "[build] backend"
  "${PROJECT_ROOT}/mvnw" -q -pl backend/ism-bootstrap -am package -DskipTests -DskipITs
  start_process backend java -jar "${PROJECT_ROOT}/backend/ism-bootstrap/target/ism-bootstrap-0.1.0-SNAPSHOT.jar" --spring.profiles.active=local
  wait_for_backend
  start_process admin corepack pnpm --filter @ism/admin dev
  start_process console corepack pnpm --filter @ism/console dev
  wait_for_frontend admin http://127.0.0.1:4173
  wait_for_frontend console http://127.0.0.1:4174
  echo
  echo "Local environment is running:"
  echo "  Admin:   http://127.0.0.1:4173  tenant=demo user=admin password=Admin@123456"
  echo "  Console: http://127.0.0.1:4174  user=platform-admin password=Admin@123456"
  echo "  API:     http://127.0.0.1:18080"
  echo "  Logs:    ./scripts/dev.sh logs"
}

down() {
  stop_process console
  stop_process admin
  stop_process backend
  if [[ -f "${ENV_FILE}" ]] && command -v docker >/dev/null 2>&1; then
    docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" down
  fi
}

status() {
  for name in backend admin console; do
    if is_running "${name}"; then
      echo "[up]   ${name} (pid $(cat "$(pid_file "${name}")"))"
    else
      echo "[down] ${name}"
    fi
  done
  if curl --silent --fail http://127.0.0.1:18080/actuator/health >/dev/null 2>&1; then
    echo "[up]   backend health"
  else
    echo "[down] backend health"
  fi
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 && [[ -f "${ENV_FILE}" ]]; then
    docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps
  fi
}

case "${1:-}" in
  up) up ;;
  down) down ;;
  restart) down; up ;;
  status) status ;;
  logs) tail -n 80 -F "$(log_file backend)" "$(log_file admin)" "$(log_file console)" ;;
  *) usage; exit 1 ;;
esac
