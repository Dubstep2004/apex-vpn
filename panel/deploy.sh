#!/usr/bin/env bash
# Разворачивает панель (Telegram-бот + сервер обновлений) на Cloudflare Workers через API.
# Нужны переменные: CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID, TELEGRAM_BOT_TOKEN, TELEGRAM_ADMIN_IDS.
# Печатает адрес панели в $GITHUB_OUTPUT (url=…) и в stdout.
set -euo pipefail
cd "$(dirname "$0")"

API="https://api.cloudflare.com/client/v4/accounts/$CLOUDFLARE_ACCOUNT_ID"
AUTH=(-H "Authorization: Bearer $CLOUDFLARE_API_TOKEN")
NAME="apex-panel"
CI_SECRET=$(printf '%s:apex-ci' "$TELEGRAM_BOT_TOKEN" | sha256sum | cut -c1-40)

cf() { curl -sS "${AUTH[@]}" "$@"; }
ok() { jq -e '.success == true' >/dev/null; }

echo "==> KV-хранилище"
KV_ID=$(cf "$API/storage/kv/namespaces?per_page=100" | jq -r '.result[] | select(.title=="apex-panel-kv") | .id' | head -1)
if [ -z "$KV_ID" ]; then
  KV_ID=$(cf -X POST "$API/storage/kv/namespaces" -H 'content-type: application/json' -d '{"title":"apex-panel-kv"}' | jq -r '.result.id')
fi
[ -n "$KV_ID" ] && [ "$KV_ID" != null ] || { echo "::error title=Панель::не удалось создать KV — проверьте права токена Cloudflare (Workers KV Storage: Edit)"; exit 1; }

echo "==> загрузка Worker"
META_FILE=$(mktemp)
jq -n --arg kv "$KV_ID" --arg bot "$TELEGRAM_BOT_TOKEN" --arg admins "$TELEGRAM_ADMIN_IDS" --arg ci "$CI_SECRET" '{
  main_module: "worker.js",
  compatibility_date: "2025-06-01",
  bindings: [
    {type: "kv_namespace", name: "APEX", namespace_id: $kv},
    {type: "secret_text", name: "BOT_TOKEN", text: $bot},
    {type: "secret_text", name: "ADMIN_IDS", text: $admins},
    {type: "secret_text", name: "CI_SECRET", text: $ci}
  ]
}' > "$META_FILE"
RES=$(cf -X PUT "$API/workers/scripts/$NAME" \
  -F "metadata=<$META_FILE;type=application/json" \
  -F "worker.js=@worker.js;type=application/javascript+module")
echo "$RES" | ok || { echo "::error title=Панель::Cloudflare не принял Worker: $(echo "$RES" | jq -c '.errors')"; exit 1; }

# Адрес *.workers.dev
cf -X POST "$API/workers/scripts/$NAME/subdomain" -H 'content-type: application/json' -d '{"enabled":true}' >/dev/null || true
SUB=$(cf "$API/workers/subdomain" | jq -r '.result.subdomain // empty')
[ -n "$SUB" ] || { echo "::error title=Панель::в Cloudflare не выбран адрес workers.dev — откройте Workers & Pages один раз и выберите его"; exit 1; }
URL="https://$NAME.$SUB.workers.dev"

echo "==> вебхук Telegram"
curl -sS "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/setWebhook" \
  -d "url=$URL/tg" -d "secret_token=$CI_SECRET" -d 'allowed_updates=["message","callback_query"]' | jq -c .
curl -sS "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/setMyCommands" -H 'content-type: application/json' -d '{"commands":[
  {"command":"start","description":"Меню"},
  {"command":"update","description":"Обновления приложения"},
  {"command":"news","description":"Объявление: /news текст"},
  {"command":"maintenance","description":"Техработы: /maintenance текст"},
  {"command":"stats","description":"Статистика"},
  {"command":"crashes","description":"Последние сбои"},
  {"command":"help","description":"Все команды"}]}' >/dev/null

echo "Панель: $URL"
[ -n "${GITHUB_OUTPUT:-}" ] && { echo "url=$URL"; echo "kv=$KV_ID"; } >> "$GITHUB_OUTPUT"
exit 0
