#!/usr/bin/env bash
#
# Java Diag 探针（java-diag-probe）启停脚本（jar / properties 与脚本同目录）
#
# 用法（须用 bash，勿用 sh start.sh；CentOS 上 sh 常为 dash，不支持 pipefail）:
#   chmod +x start.sh && ./start.sh start
#   bash start.sh start
#   bash start.sh stop | restart | status | foreground
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

APP_NAME="Java Diag Probe"
JAR="$SCRIPT_DIR/java-diag-probe.jar"
CONFIG="$SCRIPT_DIR/java-diag-probe.properties"
PID_FILE="$SCRIPT_DIR/mcp-server.pid"
LOG_DIR="$SCRIPT_DIR/logs"
LOG_FILE="$LOG_DIR/mcp-server.out"

JAVA_BIN="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
JAVA_BIN="${JAVA_BIN:-java}"

err() {
  echo "ERROR: $*" >&2
  exit 1
}

usage() {
  cat <<EOF
用法: $0 {start|stop|restart|status|foreground}

  start       后台启动（nohup，PID → mcp-server.pid，日志 → logs/mcp-server.out）
  stop        停止后台进程
  restart     先 stop 再 start
  status      查看运行状态
  foreground  前台运行（Ctrl+C 停止）
EOF
}

require_java_and_files() {
  if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
    err "未找到 Java（请安装 JDK 17+ 或设置 JAVA_HOME）。尝试命令: $JAVA_BIN"
  fi

  # java -version 写到 stderr；版本行常见形态：
  #   openjdk version "17.0.11" 2024-07-16
  #   openjdk 17 2024-07-16 LTS
  # 旧 sed 只替换到闭引号，会把 " 后的日期残留进变量，导致 [[ -lt ]] 语法错误。
  local ver_line raw java_major
  ver_line="$("$JAVA_BIN" -version 2>&1 | head -n1 || true)"
  raw="$(printf '%s\n' "$ver_line" | sed -n 's/.*"\([^"]*\)".*/\1/p')"
  if [[ -z "$raw" ]]; then
    raw="$(printf '%s\n' "$ver_line" | sed -n 's/^[^0-9]*\([0-9][0-9.]*\).*/\1/p')"
  fi
  case "$raw" in
    1.*) java_major="$(printf '%s\n' "$raw" | cut -d. -f2)" ;; # 1.8.x → 8
    *)   java_major="$(printf '%s\n' "$raw" | cut -d. -f1)" ;;
  esac
  if [[ ! "${java_major:-}" =~ ^[0-9]+$ ]]; then
    err "无法解析 Java 版本（原始: ${ver_line:-<empty>}），请确认: $JAVA_BIN -version"
  fi
  if [[ "$java_major" -lt 17 ]]; then
    err "需要 Java 17 或更高版本，当前 major=$java_major ($ver_line)"
  fi

  [[ -f "$JAR" ]] || err "缺少 jar: $JAR"
  [[ -f "$CONFIG" ]] || err "缺少配置: $CONFIG"
}

read_pid() {
  if [[ ! -f "$PID_FILE" ]]; then
    echo ""
    return
  fi
  tr -d '[:space:]' <"$PID_FILE" || true
}

is_running() {
  local pid="$1"
  [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null
}

cmd_status() {
  local pid
  pid="$(read_pid)"
  if is_running "$pid"; then
    echo "$APP_NAME 正在运行 pid=$pid"
    echo "  配置: $CONFIG"
    echo "  日志: $LOG_FILE"
    return 0
  fi
  if [[ -n "$pid" ]]; then
    echo "$APP_NAME 未运行（PID 文件残留 pid=$pid，可执行 stop 清理）"
    return 3
  fi
  echo "$APP_NAME 未运行"
  return 3
}

cmd_start() {
  require_java_and_files

  local pid
  pid="$(read_pid)"
  if is_running "$pid"; then
    err "$APP_NAME 已在运行 (pid=$pid)。可先: $0 status | $0 stop"
  fi
  if [[ -n "$pid" ]]; then
    rm -f "$PID_FILE"
  fi

  mkdir -p "$LOG_DIR"
  echo "==== $(date -Iseconds 2>/dev/null || date) start java-diag-probe ====" >>"$LOG_FILE"
  nohup "$JAVA_BIN" -jar "$JAR" --config "$CONFIG" >>"$LOG_FILE" 2>&1 &
  local new_pid=$!
  echo "$new_pid" >"$PID_FILE"
  sleep 0.5
  if ! is_running "$new_pid"; then
    rm -f "$PID_FILE"
    err "启动失败，请查看日志: $LOG_FILE"
  fi
  echo "$APP_NAME 已启动 pid=$new_pid"
  echo "  日志: $LOG_FILE"
}

cmd_stop() {
  local pid
  pid="$(read_pid)"
  if [[ -z "$pid" ]]; then
    echo "未找到 $PID_FILE，$APP_NAME 可能未以后台方式启动。"
    return 0
  fi

  if ! is_running "$pid"; then
    echo "进程 $pid 不存在，清理 $PID_FILE"
    rm -f "$PID_FILE"
    return 0
  fi

  echo "正在停止 $APP_NAME (pid=$pid)..."
  kill "$pid" 2>/dev/null || true

  local _
  for _ in $(seq 1 30); do
    if ! is_running "$pid"; then
      rm -f "$PID_FILE"
      echo "已停止。"
      return 0
    fi
    sleep 1
  done

  if is_running "$pid"; then
    echo "SIGTERM 超时，发送 SIGKILL..."
    kill -9 "$pid" 2>/dev/null || true
    sleep 1
  fi

  rm -f "$PID_FILE"
  echo "已停止。"
}

cmd_restart() {
  cmd_stop || true
  cmd_start
}

cmd_foreground() {
  require_java_and_files
  local pid
  pid="$(read_pid)"
  if is_running "$pid"; then
    err "$APP_NAME 已在后台运行 (pid=$pid)。请先: $0 stop"
  fi
  echo "前台启动 $APP_NAME（Ctrl+C 停止）..."
  exec "$JAVA_BIN" -jar "$JAR" --config "$CONFIG"
}

cmd="${1:-}"
case "$cmd" in
  start) cmd_start ;;
  stop) cmd_stop ;;
  restart) cmd_restart ;;
  status) cmd_status ;;
  foreground|fg) cmd_foreground ;;
  -h|--help|help|"") usage; [[ -n "$cmd" ]] || exit 1 ;;
  *)
    echo "未知命令: $cmd" >&2
    usage
    exit 1
    ;;
esac
