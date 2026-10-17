#!/usr/bin/env bash
# 本地演示数据库使用独立命名卷；stop 不删除数据。
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
ENV_FILE="$ROOT/docs/TelecomAIPrecheck/.env"
if [[ ! -f "$ENV_FILE" ]]; then
  (umask 077; python3 - "$ENV_FILE" <<'PY'
import secrets,sys
from pathlib import Path
lines=['MYSQL_PORT=13307','DB_USERNAME=precheck','SERVER_PORT=8080','SPRING_PROFILES_ACTIVE=mysql']
for name in ['DB_PASSWORD','MYSQL_ROOT_PASSWORD','DEMO_OPERATOR_PASSWORD','DEMO_REVIEWER_PASSWORD','DEMO_AUDITOR_PASSWORD']:
    lines.append(name+'='+secrets.token_urlsafe(24))
Path(sys.argv[1]).write_text('\n'.join(lines)+'\n')
PY
  )
  echo "已生成本机演示配置：${ENV_FILE}（已被 Git 忽略）"
fi
set -a
source "$ENV_FILE"
set +a
export DB_URL="jdbc:mysql://127.0.0.1:${MYSQL_PORT}/telecom_ai_precheck?serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false"
compose=(docker compose --env-file "$ENV_FILE" -p telecom-ai-precheck-demo -f "$ROOT/docs/TelecomAIPrecheck/docker-compose.yml")
case "${1:-help}" in
  up) "${compose[@]}" up -d --wait ;;
  backend) cd "$ROOT"; exec mvn -f backend/pom.xml spring-boot:run ;;
  stop) "${compose[@]}" down ;;
  status) "${compose[@]}" ps ;;
  *) echo "用法: scripts/demo.sh up|backend|stop|status" ;;
esac
