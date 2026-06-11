#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

LOG_DIR="${ROOT_DIR}/server/src/main/logs"
LOG_FILE="${LOG_DIR}/dejebu-server.log"
DATA_DIR="${ROOT_DIR}/server/data"

COMPOSE_CMD=()

detect_compose() {
  if docker compose version &>/dev/null; then
    COMPOSE_CMD=(docker compose)
  elif command -v docker-compose &>/dev/null; then
    COMPOSE_CMD=(docker-compose)
  else
    echo "錯誤: 找不到 docker compose，請先安裝 Docker Desktop 或 docker-compose。"
    exit 1
  fi
}

check_docker() {
  if ! command -v docker &>/dev/null; then
    echo "錯誤: 找不到 docker，請先安裝 Docker。"
    exit 1
  fi
  if ! docker info &>/dev/null; then
    echo "錯誤: Docker 未啟動，請先開啟 Docker Desktop 或 docker daemon。"
    exit 1
  fi
}

compose_quiet() {
  COMPOSE_PROGRESS=quiet "${COMPOSE_CMD[@]}" "$@" >/dev/null 2>&1
}

usage() {
  cat <<'EOF'
用法: ./start-server.sh [指令]

指令:
  (預設)    重新編譯並啟動伺服器
  start     建置並啟動（不先停止現有容器）
  stop      停止伺服器
  restart   重新編譯並啟動（同預設）
  logs      查看伺服器日誌（logback 檔案）
  status    查看容器狀態

啟動後：
  WebSocket: ws://localhost:8080/ws/game
  登入 API:  http://localhost:8080/api/auth/login
  PostgreSQL: localhost:5432（容器內服務名 postgres）

日誌檔案:
  server/src/main/logs/dejebu-server.log

資料庫持久化:
  server/data/pgdata/
EOF
}

ensure_log_dir() {
  mkdir -p "$LOG_DIR"
}

ensure_data_dir() {
  mkdir -p "$DATA_DIR/pgdata"
}

wait_for_postgres() {
  local i
  for i in {1..30}; do
    if docker inspect -f '{{.State.Health.Status}}' dejebu-postgres 2>/dev/null | grep -q healthy; then
      return 0
    fi
    if docker inspect -f '{{.State.Status}}' dejebu-postgres 2>/dev/null | grep -q restarting; then
      echo "錯誤: PostgreSQL 啟動失敗（容器不斷重啟）。"
      echo "  常見原因: server/data/pgdata 權限或內容異常。"
      echo "  可嘗試: ./start-server.sh stop && rm -rf server/data/pgdata && ./start-server.sh start"
      return 1
    fi
    sleep 1
  done
  echo "錯誤: PostgreSQL 健康檢查逾時。"
  return 1
}

wait_for_server() {
  local i
  for i in {1..60}; do
    if curl -sf http://localhost:8080/api/auth/health >/dev/null 2>&1; then
      return 0
    fi
    if ! docker inspect -f '{{.State.Running}}' dejebu-server 2>/dev/null | grep -q true; then
      local status
      status="$(docker inspect -f '{{.State.Status}}' dejebu-server 2>/dev/null || echo unknown)"
      if [[ "$status" == "exited" || "$status" == "dead" ]]; then
        echo "錯誤: DeJaBu Server 容器已停止 (status: $status)。"
        echo "  請查看: server/src/main/logs/dejebu-server.log"
        return 1
      fi
    fi
    sleep 1
  done
  echo "警告: 伺服器尚未在 8080 端口回應，可能仍在啟動中。"
  echo "  請稍候或查看: server/src/main/logs/dejebu-server.log"
  return 0
}

cmd_start() {
  echo "==> 建置並啟動 DeJaBu Server..."
  ensure_log_dir
  ensure_data_dir
  local build_log
  build_log=$(mktemp)
  if ! "${COMPOSE_CMD[@]}" build --progress=plain >"$build_log" 2>&1; then
    echo "錯誤: docker compose build 失敗。編譯錯誤如下："
    cat "$build_log"
    rm -f "$build_log"
    exit 1
  fi
  rm -f "$build_log"
  if ! compose_quiet up -d --force-recreate; then
    echo "錯誤: docker compose up 失敗。"
    "${COMPOSE_CMD[@]}" ps
    exit 1
  fi

  echo "==> 等待 PostgreSQL 就緒..."
  if ! wait_for_postgres; then
    "${COMPOSE_CMD[@]}" ps
    exit 1
  fi

  echo "==> 等待 DeJaBu Server 就緒..."
  wait_for_server

  echo ""
  echo "伺服器已啟動。"
  echo "  WebSocket: ws://localhost:8080/ws/game"
  echo "  登入 API:  http://localhost:8080/api/auth/login"
  echo "  PostgreSQL: localhost:5432 (user/pass/db: dejebu)"
  echo "  資料目錄:  server/data/pgdata/"
  echo "  日誌檔案:  server/src/main/logs/dejebu-server.log"
  echo "  查看日誌:  ./start-server.sh logs"
  echo "  停止服務:  ./start-server.sh stop"
}

cmd_stop() {
  echo "==> 停止 DeJaBu Server..."
  compose_quiet down
  echo "伺服器已停止。"
}

cmd_restart() {
  echo "==> 重新編譯並啟動 DeJaBu Server..."
  cmd_stop
  cmd_start
}

cmd_logs() {
  ensure_log_dir
  if [[ ! -f "$LOG_FILE" ]]; then
    echo "日誌檔案尚不存在: server/src/main/logs/dejebu-server.log"
    echo "請先啟動伺服器，或稍候服務寫入第一筆日誌。"
    exit 1
  fi
  tail -f "$LOG_FILE"
}

cmd_status() {
  "${COMPOSE_CMD[@]}" ps
}

main() {
  check_docker
  detect_compose

  local action="${1:-restart}"
  case "$action" in
    start) cmd_start ;;
    stop) cmd_stop ;;
    restart) cmd_restart ;;
    logs) cmd_logs ;;
    status) cmd_status ;;
    -h|--help|help) usage ;;
    *)
      echo "未知指令: $action"
      usage
      exit 1
      ;;
  esac
}

main "$@"
