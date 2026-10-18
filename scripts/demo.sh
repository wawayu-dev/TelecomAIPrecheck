#!/usr/bin/env bash
# 本地演示数据库使用独立命名卷；stop 不删除数据。
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
ENV_FILE="$ROOT/docs/TelecomAIPrecheck/.env"
(umask 077; python3 - "$ENV_FILE" <<'PY'
import secrets,sys
from pathlib import Path
path=Path(sys.argv[1])
text=path.read_text() if path.exists() else ''
existing={line.split('=',1)[0] for line in text.splitlines() if '=' in line and not line.lstrip().startswith('#')}
defaults={'MYSQL_PORT':'13307','FRONTEND_PORT':'8088','RELEASE_TAG':'local',
          'DB_USERNAME':'precheck','DEMO_ONE_CLICK_LOGIN':'true'}
secrets_required=['DB_PASSWORD','MYSQL_ROOT_PASSWORD','DEMO_OPERATOR_PASSWORD','DEMO_REVIEWER_PASSWORD',
                  'DEMO_AUDITOR_PASSWORD','DEMO_HOST_PASSWORD']
additions=[f'{name}={value}' for name,value in defaults.items() if name not in existing]
additions += [f'{name}={secrets.token_urlsafe(24)}' for name in secrets_required if name not in existing]
if additions:
    prefix='' if not text or text.endswith('\n') else '\n'
    path.write_text(text+prefix+'\n'.join(additions)+'\n')
PY
)
chmod 600 "$ENV_FILE"
set -a
source "$ENV_FILE"
set +a
export DEMO_ONE_CLICK_LOGIN="${DEMO_ONE_CLICK_LOGIN:-true}"
compose=(docker compose --env-file "$ENV_FILE" -p telecom-ai-precheck-demo -f "$ROOT/docs/TelecomAIPrecheck/docker-compose.yml")
case "${1:-help}" in
  up) "${compose[@]}" up -d --build --wait ;;
  build) "${compose[@]}" build ;;
  logs) "${compose[@]}" logs -f backend frontend ;;
  stop) "${compose[@]}" down ;;
  status) "${compose[@]}" ps ;;
  *) echo "用法: scripts/demo.sh up|build|logs|stop|status" ;;
esac
