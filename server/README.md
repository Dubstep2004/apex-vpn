# Сервер обновлений APEX VPN (ваш VPS)

На VPS лежат APK, `update.json`, `news.json`; туда же приходят отчёты о сбоях.
Ключ подписи хранится только на сервере: `apex publish` переподписывает APK, поэтому
все версии подписаны одним ключом и ставятся поверх друг друга.

## Установка (Ubuntu / Debian, один раз)

```bash
git clone https://github.com/Dubstep2004/apex-vpn.git   # или скопируйте папку server/
sudo bash apex-vpn/server/install.sh vpn.example.com     # ваш домен
```

Домен должен указывать на IP сервера (A-запись). Если порт 443 занят VPN-сервером,
сайт поднимется на 8443 — адрес скрипт напечатает сам.

В конце скрипт выдаст адрес вида `https://vpn.example.com/apexvpn`. Добавьте его в GitHub:
**Settings → Secrets and variables → Actions → Variables → New variable**,
Name `SERVER_URL`, Value — этот адрес. Сборки после этого знают, куда смотреть за обновлениями.

## Команды

| команда | что делает |
|---|---|
| `apex publish 1.4.2` | скачать версию с GitHub Releases, подписать, выпустить |
| `apex publish 1.4.2 --mandatory` | то же, обновление обязательное |
| `apex publish ./app.apk --code 9 --name 1.4.2` | выпустить свой файл |
| `apex unpublish` | снять обновление |
| `apex news "Текст" --title "Заголовок"` | объявление на главном экране (`--warning` — жёлтое, `--url` — ссылка) |
| `apex news off` | убрать объявление |
| `apex crashes` | последние отчёты о сбоях (`apex crashes 20`, `apex crashes clear`) |
| `apex status` | что сейчас выпущено |

Ссылка для установки на телефон: `https://ДОМЕН/apexvpn/apex-vpn-latest.apk`
(или страница `https://ДОМЕН/apexvpn/`).

Если репозиторий приватный, впишите токен GitHub (доступ Contents: Read) в `/etc/apexvpn/config`
(`GITHUB_TOKEN="…"`) — иначе `apex publish` не сможет скачать сборку.

**Сделайте копию** `/etc/apexvpn/apex.jks` и `/etc/apexvpn/keypass`: без них новые версии
не установятся поверх старых.

## Файлы

- `install.sh` — установка (Caddy с https, сервис приёма сбоев, ключ подписи, команда `apex`)
- `apex` — команда управления
- `crash-receiver.py` — приём отчётов о сбоях (только 127.0.0.1, наружу через Caddy)
- `news.json` — пример объявления
