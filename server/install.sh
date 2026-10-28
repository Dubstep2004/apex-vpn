#!/usr/bin/env bash
# Установка сервера APEX VPN на Ubuntu / Debian: объявления, отчёты о сбоях,
# страница для скачивания (ведёт на GitHub Releases). Обновления приложения — с GitHub.
#
#   sudo bash install.sh vpn.example.com
#
# Что делает:
#   • Caddy — https с бесплатным сертификатом Let's Encrypt (продлевается сам)
#   • https://ДОМЕН/apexvpn/ — news.json (объявления) и страница для скачивания
#   • https://ДОМЕН/apexvpn/crash — приём отчётов о сбоях (сервис apexvpn-crash)
#   • ключ подписи — можно добавить в секреты GitHub (меню apex, пункт 15)
#   • команда apex — меню управления
#
# Если порт 443 уже занят (например, VPN-сервером), сайт поднимется на 8443.
set -euo pipefail

DOMAIN="${1:-}"
[[ $EUID -eq 0 ]] || { echo "Запустите через sudo"; exit 1; }
[[ -n "$DOMAIN" ]] || { echo "Использование: sudo bash install.sh vpn.example.com"; exit 1; }
HERE="$(cd "$(dirname "$0")" && pwd)"

echo "==> пакеты"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq curl jq unzip openssl python3 default-jre-headless debian-keyring debian-archive-keyring apt-transport-https gnupg >/dev/null

if ! command -v caddy >/dev/null; then
  echo "==> Caddy"
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -qq && apt-get install -y -qq caddy >/dev/null
fi

busy() { ss -Hltn "sport = :$1" | grep -q . && ! ss -Hltnp "sport = :$1" | grep -q caddy; }
PORT=443
if busy 443; then
  PORT=8443
  echo "⚠️  Порт 443 занят другой программой — сайт будет на порту 8443"
  busy 80 && { echo "❌ Заняты и 443, и 80: Let's Encrypt не сможет выдать сертификат. Освободите порт 80 и запустите снова."; exit 1; }
  busy 8443 && { echo "❌ Порт 8443 тоже занят."; exit 1; }
fi
SITE="$DOMAIN"; [[ $PORT == 443 ]] || SITE="$DOMAIN:$PORT"
BASE_URL="https://$SITE/apexvpn"

echo "==> папки"
mkdir -p /var/www/apexvpn /var/lib/apexvpn/crashes /etc/apexvpn /usr/local/lib/apexvpn /var/log/caddy
[[ -f /var/www/apexvpn/news.json ]] || echo '[]' > /var/www/apexvpn/news.json
chmod 755 /var/www/apexvpn; chmod 644 /var/www/apexvpn/news.json

echo "==> ключ подписи"
if [[ ! -f /etc/apexvpn/apex.jks ]]; then
  head -c 24 /dev/urandom | base64 | tr -d '/+=' > /etc/apexvpn/keypass
  keytool -genkeypair -keystore /etc/apexvpn/apex.jks -storetype PKCS12 -alias apex -keyalg RSA -keysize 4096 \
    -validity 10000 -storepass "$(cat /etc/apexvpn/keypass)" -keypass "$(cat /etc/apexvpn/keypass)" \
    -dname "CN=APEX VPN, O=APEX VPN" >/dev/null 2>&1
  echo "   создан /etc/apexvpn/apex.jks — сделайте копию: без него обновления не встанут поверх"
fi
chmod 600 /etc/apexvpn/apex.jks /etc/apexvpn/keypass

# Токен GitHub и зеркала сохраняем при переустановке и смене домена
OLD_TOKEN=""
OLD_SYNC=""; OLD_URLS=""; OLD_GH=""
if [[ -f /etc/apexvpn/config ]]; then
  OLD_TOKEN="$(sed -nE 's/^GITHUB_TOKEN="(.*)"$/\1/p' /etc/apexvpn/config)"
  OLD_SYNC="$(sed -nE 's/^MIRROR_SYNC="(.*)"$/\1/p' /etc/apexvpn/config)"
  OLD_URLS="$(sed -nE 's/^MIRROR_URLS="(.*)"$/\1/p' /etc/apexvpn/config)"
  OLD_GH="$(sed -nE 's/^MIRROR_GITHUB="(.*)"$/\1/p' /etc/apexvpn/config)"
fi
cat > /etc/apexvpn/config <<EOF
# Настройки команды apex
BASE_URL="$BASE_URL"
REPO="Dubstep2004/apex-vpn"
# Для приватного репозитория: токен GitHub с доступом на чтение (Contents: Read)
GITHUB_TOKEN="$OLD_TOKEN"
# Зеркала APK (пункт 21 меню): копировать по ssh, откуда скачивать, загружать ли в релиз GitHub
MIRROR_SYNC="$OLD_SYNC"
MIRROR_URLS="$OLD_URLS"
MIRROR_GITHUB="$OLD_GH"
EOF
chmod 600 /etc/apexvpn/config

echo "==> приём отчётов о сбоях"
id apexvpn >/dev/null 2>&1 || useradd --system --no-create-home --shell /usr/sbin/nologin apexvpn
chown apexvpn:apexvpn /var/lib/apexvpn/crashes
chmod 755 /var/lib/apexvpn/crashes
install -m 755 "$HERE/crash-receiver.py" /usr/local/lib/apexvpn/crash-receiver.py
cat > /etc/systemd/system/apexvpn-crash.service <<'EOF'
[Unit]
Description=APEX VPN crash reports
After=network.target

[Service]
ExecStart=/usr/bin/python3 /usr/local/lib/apexvpn/crash-receiver.py
User=apexvpn
Group=apexvpn
NoNewPrivileges=yes
ProtectSystem=strict
ReadWritePaths=/var/lib/apexvpn/crashes
Environment=APEX_CRASH_DIR=/var/lib/apexvpn/crashes
Restart=always

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now apexvpn-crash >/dev/null
systemctl restart apexvpn-crash

echo "==> Caddy: $SITE"
chown caddy:caddy /var/log/caddy 2>/dev/null || true
cat > /etc/caddy/apexvpn.caddy <<EOF
$SITE {
	log {
		output file /var/log/caddy/apexvpn-access.log {
			roll_size 10MiB
			roll_keep 5
			roll_keep_for 168h
		}
		format json
	}
	handle /apexvpn/crash {
		request_body {
			max_size 64KB
		}
		reverse_proxy 127.0.0.1:8787
	}
	handle /apexvpn* {
		root * /var/www
		@json path *.json
		header @json Cache-Control "no-store"
		file_server
	}
	handle {
		redir /apexvpn/ 302
	}
}
EOF
# Подключаем свой файл к основному Caddyfile, не трогая чужие сайты
if [[ ! -f /etc/caddy/Caddyfile ]] || grep -q "root \* /usr/share/caddy" /etc/caddy/Caddyfile; then
  echo "import apexvpn.caddy" > /etc/caddy/Caddyfile   # стандартная заглушка Caddy — заменяем
elif ! grep -q "import apexvpn.caddy" /etc/caddy/Caddyfile; then
  echo "import apexvpn.caddy" >> /etc/caddy/Caddyfile
fi
caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null
systemctl enable caddy >/dev/null
systemctl reload caddy 2>/dev/null || systemctl restart caddy

install -m 755 "$HERE/apex" /usr/local/bin/apex
# Таймеры: еженедельное обслуживание (сразу) и автопубликация (включается в меню, пункт 17)
/usr/local/bin/apex setup-units || true

echo
echo "✅ Готово. Адрес сервера:"
echo
echo "     $BASE_URL"
echo
echo "Дальше:"
echo "  1. Адрес сервера вписан в приложение при сборке (переменная SERVER_URL в GitHub"
echo "     или значение по умолчанию в app/build.gradle.kts) — пропишите туда: $BASE_URL"
echo "  2. Объявления и отчёты о сбоях: sudo apex"
echo "  3. Страница для скачивания: $BASE_URL/ — ведёт на последнюю версию на GitHub"
echo
echo "Меню управления: apex"
