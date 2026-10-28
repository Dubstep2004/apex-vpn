#!/usr/bin/env bash
# Загружает APK в панель (KV частями по 20 МБ) и сообщает боту о новой сборке.
# Аргументы: <apk> <versionCode> <versionName> <changelog-file>
# Переменные: CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID, TELEGRAM_BOT_TOKEN, PANEL_URL, PANEL_KV, RUN_URL
set -euo pipefail
APK="$1"; CODE="$2"; NAME="$3"; NOTES="$4"
API="https://api.cloudflare.com/client/v4/accounts/$CLOUDFLARE_ACCOUNT_ID/storage/kv/namespaces/$PANEL_KV/values"
CI_SECRET=$(printf '%s:apex-ci' "$TELEGRAM_BOT_TOKEN" | sha256sum | cut -c1-40)

WORK=$(mktemp -d)
split -b 20m -d -a 2 "$APK" "$WORK/part-"
PARTS=0
for f in "$WORK"/part-*; do
  curl -sS -X PUT "$API/apk:$CODE:$PARTS" -H "Authorization: Bearer $CLOUDFLARE_API_TOKEN" \
    -H 'content-type: application/octet-stream' --data-binary "@$f" | jq -e '.success == true' >/dev/null \
    || { echo "::error title=Панель::не удалось загрузить часть $PARTS"; exit 1; }
  PARTS=$((PARTS + 1))
done

jq -n --argjson code "$CODE" --arg name "$NAME" --arg sha "$(sha256sum "$APK" | cut -d' ' -f1)" \
  --argjson size "$(stat -c%s "$APK")" --argjson parts "$PARTS" --rawfile notes "$NOTES" --arg run "${RUN_URL:-}" \
  '{versionCode:$code, versionName:$name, sha256:$sha, size:$size, parts:$parts, changelog:$notes, runUrl:$run}' \
  | curl -sS -f -X POST "$PANEL_URL/ci/build" -H "Authorization: Bearer $CI_SECRET" -H 'content-type: application/json' -d @- 
echo
echo "==> сборка $NAME ($PARTS частей) отправлена в панель"
