<div align="center">

<img src="docs/img/hero.webp" alt="APEX VPN" width="100%">

<br>

<p>
<a href="https://github.com/Dubstep2004/apex-vpn/releases/latest"><b>⬇️ Скачать APK</b></a>
&nbsp;&nbsp;·&nbsp;&nbsp;
<a href="#-что-нового"><b>🆕 Что нового в 1.7.1</b></a>
&nbsp;&nbsp;·&nbsp;&nbsp;
<a href="server/README.md"><b>🖥️ Свой сервер</b></a>
</p>

<h3>Быстрый и умный VPN-клиент для Android</h3>

<p>Два ядра · все популярные протоколы · 9 дизайнов 2026 · карта подключения · свой сервер обновлений</p>

<p>
<a href="#-возможности">Возможности</a> ·
<a href="#-дизайны-2026">Дизайны</a> ·
<a href="#-протоколы">Протоколы</a> ·
<a href="#-свой-сервер-vps">Свой сервер</a> ·
<a href="#-сборка">Сборка</a> ·
<a href="#-что-нового">Что нового</a>
</p>

</div>

---

> [!NOTE]
> APEX VPN работает только с подпиской `https://sub.knownhosts.ru/…`. Ссылку можно вставить, отсканировать по QR-коду или открыть через `apexvpn://import?url=…`.

## ✨ Возможности

<div align="center">
<img src="docs/img/screens.webp" alt="Экраны APEX VPN" width="100%">
<br><sub>Иллюстрации интерфейса</sub>
</div>

<table>
<tr>
<td width="50%" valign="top">

### 🛡️ Подключение
- **Автопилот** выбирает самый быстрый сервер и переключается, если сервер перестал отвечать
- **Смена сервера без разрыва** VPN
- **Аварийный выключатель**: если VPN упал, интернет закрыт до переподключения
- **Профили**: Обычный, Игры, Звонки, Экономия батареи

</td>
<td width="50%" valign="top">

### 🧭 Маршрутизация
- **Умный режим**: российские сайты и IP идут напрямую
- **Сервисы через VPN**: YouTube, Instagram, Telegram, ChatGPT, Discord и другие
- **Для опытных**: свои правила, FakeDNS, стратегия доменов, DoH / DoT / DoQ
- **Банки не увидят VPN**, выбор приложений

</td>
</tr>
<tr>
<td valign="top">

### 🗺️ Главный экран
- **Кнопка** или **Карта** с дугой от вас до сервера
- **Живая карта**: дышащие кольца и бегущий по дуге свет
- Живой график пинга, скорость, таймер отключения
- Карточка подписки: трафик, срок, объявления, поддержка

</td>
<td valign="top">

### 🎨 Оформление и ощущения
- **9 дизайнов**, 4 темы, 5 цветов, 5 иконок
- **Звук подключения**: Сигнал, Кристалл, Сфера, Звон (на беззвучном телефон молчит)
- **Вибрация**, **плавный режим**, **размытие стекла**

</td>
</tr>
<tr>
<td valign="top">

### 📡 Серверы
- Фильтр по протоколу с числом серверов
- Настоящий пинг через VPN, а без VPN проверка по TCP / QUIC
- Описания серверов в формате Happ; серверы с рекламой собраны в отдельный раздел
- Избранное, вкладка «Только LTE», ярлыки серверов

</td>
<td valign="top">

### 🧩 Удобство
- Плитка в шторке, 3 виджета, **Wear OS**
- Голос: «включи VPN в APEX VPN»
- Правила Wi-Fi, раздача VPN на компьютер
- Блокировка рекламы, **семейный режим** (18+, казино)

</td>
</tr>
</table>

## 🎨 Дизайны 2026

<div align="center">
<img src="docs/img/designs.webp" alt="9 дизайнов" width="100%">
</div>

| Дизайн | Характер |
|---|---|
| **Классика** | спокойный тёмный или светлый |
| **Liquid Glass** | стекло и цветные пятна за ним |
| **Material Expressive** | цвет берётся из обоев (Android 12+) |
| **Neon Cyber** | неоновая сетка, розовый и голубой |
| **Минимализм** | только чёрное и белое |
| **Aurora** | плывущее северное сияние |
| **Бенто** | главный экран из плиток |
| **Ретро-терминал** | зелёный моноширинный шрифт |
| **Пастель** | светлый и мягкий |

Выбирается здесь: **Настройки → Дизайн, тема и иконка**.

## 🔌 Протоколы

Два ядра, как в Happ: **sing-box 1.13.21** и **Xray 26**. Выбор в **Настройки → Ядро**:
- **Авто**: Xray включается только для того, что не умеет sing-box;
- **Только Xray**;
- **Только sing-box**.

| | |
|---|---|
| **VLESS** | Reality, Vision, WS, gRPC, HTTP/2, HTTPUpgrade, QUIC, **XHTTP**, **VLESS Encryption** |
| **VMess · Trojan** | все транспорты, **mKCP** через Xray |
| **Hysteria · Hysteria 2** | со сменой портов и Salamander |
| **TUIC · WireGuard · AnyTLS** | |
| **NaiveProxy · ShadowTLS · SSH** | |
| **SOCKS 4/5 · HTTP(S)** | цепочки серверов (detour) из JSON-подписок |

<details>
<summary>Не поддерживается</summary>

- ShadowsocksR и AmneziaWG: их нет в ядрах.
- Shadowsocks скрыт в приложении.

</details>

## 🖥️ Свой сервер (VPS)

На вашем VPS лежат APK, `update.json` и `news.json`, туда же приходят отчёты о сбоях. Ключ подписи хранится только там.

```bash
curl -fsSL -o apex https://raw.githubusercontent.com/Dubstep2004/apex-vpn/main/server/apex && sudo bash apex
```

Откроется меню с номерами: установка, выпуск версии, объявления, отчёты о сбоях, резервная копия, сертификат, обновление скрипта. Потом меню открывается командой **`sudo apex`**.

Подробно: **[server/README.md](server/README.md)**

## 🛠️ Сборка

<details open>
<summary><b>Через GitHub Actions (проще всего)</b></summary>

📘 **[Пошаговая инструкция с картинками →](docs/GUIDE.md)**

<a href="docs/GUIDE.md"><img src="docs/guide/step2.webp" width="49%"></a> <a href="docs/GUIDE.md"><img src="docs/guide/step3.webp" width="49%"></a>

1. **Сервер на VPS**: `sudo apex` → пункт 1, введите домен. Скопируйте адрес, который напечатает скрипт.
2. **GitHub → Settings → Secrets and variables → Actions → Variables**: добавьте `SERVER_URL` с этим адресом.
3. **Ключ подписи (один раз)**: на VPS `sudo apex` → пункт 22, затем в **Secrets** добавьте `APEX_KEYSTORE_PASSWORD` и `APEX_KEYSTORE_BASE64`. После этого все версии подписаны одним ключом и ставятся друг поверх друга.
4. **Actions → Build APEX VPN → Run workflow** (или запушьте в `main`). Через ~25 минут в **Releases** появится `apex-vpn-<версия>.apk`.
5. **На сервере**: `sudo apex publish <версия>` (или включите автопубликацию, пункт 17).
6. **На телефоне**: один раз удалите старую версию, затем откройте `https://ваш-домен/apexvpn/` и установите.

> [!IMPORTANT]
> Ошибка **«конфликтует с другим пакетом»** значит, что старое и новое приложение подписаны разными ключами. Удалите APEX VPN один раз и поставьте заново. Шаг 3 делает так, чтобы это больше не повторялось.

</details>

<details>
<summary><b>Локально</b></summary>

Нужны JDK 17, Go 1.25+, Android SDK и NDK `28.0.13004108`.

```bash
export ANDROID_HOME=~/Android/Sdk
./core/build-core.sh                      # ядро → app/libs/libbox.aar
./core/fetch-xray.sh                      # Xray → jniLibs
./gradlew assembleRelease
```

</details>

<details>
<summary><b>Структура проекта</b></summary>

```
app/                    Android-приложение (Kotlin, Jetpack Compose)
  …/vpn/                VpnService, ядра sing-box и Xray, виджеты, ярлыки
  …/data/               подписка, настройки, пинг, звуки, обновление баз
  …/update/             обновление приложения
  …/ui/                 экраны, темы, карта
wear/                   приложение для часов Wear OS
core/apexcore/          Go: разбор подписки → конфиги sing-box и Xray (+ тесты)
server/                 сервер обновлений для VPS (install.sh, меню apex)
.github/workflows/      сборка APK
```

</details>

<details>
<summary><b>Как выпустить обновление</b></summary>

1. В `app/build.gradle.kts` увеличьте `versionCode` и `versionName`, добавьте запись в `data/Changelog.kt`.
2. Дождитесь сборки на GitHub.
3. На сервере выполните `apex publish <версия>` (с `--mandatory` обновление станет обязательным).
4. Пользователи увидят обновление при следующем запуске. По Wi-Fi файл скачивается заранее, а установщик открывается сам.

| поле `update.json` | смысл |
|---|---|
| `versionCode` | номер сборки; обновление показывается, если он больше установленного |
| `versionName` | версия для пользователя |
| `apkUrl` | прямая https-ссылка на APK |
| `sha256` | контрольная сумма APK |
| `changelog` | что нового |
| `mandatory` | `true`: обновление нельзя отложить |
| `minSupportedVersionCode` | версии ниже этого номера обязаны обновиться |

</details>

<details>
<summary><b>Подписка и Remnawave</b></summary>

- Приложение понимает base64-список ссылок, обычный список и JSON sing-box.
- Заголовки ответа: `profile-title`, `announce`, `support-url`, `profile-web-page-url`, `subscription-userinfo`, `profile-update-interval`. Их можно передать и строками `#имя: значение` в теле подписки.
- User-Agent: `APEX VPN/<версия>`.
- Remnawave отдаёт «Описание сервера» только Happ и INCY. Чтобы его видел APEX, добавьте правило ответа (Response Rule) с `additionalExtendedClientsRegex: ["^APEX VPN/"]`.

</details>

<details>
<summary><b>Отчёты о сбоях и приватность</b></summary>

Приложение отправляет на ваш сервер текст ошибки, модель телефона и версию Android. Ссылка подписки, IP и списки сайтов в отчёт не попадают. Отправку можно отключить в настройках. Посмотреть отчёты на сервере: `apex crashes`.

</details>

## 📝 Что нового

**1.7.1**
- Исправлена ошибка «конфликтует с другим пакетом»: постоянный ключ подписи сборок
- Пошаговая инструкция с картинками: [docs/GUIDE.md](docs/GUIDE.md)

**1.7.0**
- Свайп по щиту меняет сервер
- Фон за щитом: волны, частицы, звёзды
- Иконка со статусом VPN
- Отчёт в поддержку одной кнопкой
- Живые обои и виджет 4×2 с серверами
- WARP-цепочка и правила по портам
- Android TV
- Запасной адрес для скачивания обновлений
- На VPS: автопубликация, сводка, здоровье сервера, еженедельное обслуживание, зеркала APK

**1.6.0**
- Главный экран «Карта», розовый цвет, звуки и вибрация
- Маршрутизация для опытных, меню на VPS

Полная история: [`data/Changelog.kt`](app/src/main/java/com/apexvpn/client/data/Changelog.kt) и [Releases](https://github.com/Dubstep2004/apex-vpn/releases).

---

<div align="center">
<sub>Ядро sing-box распространяется по GPL-3.0, поэтому и приложение распространяется на условиях GPL-3.0. Флаги стран: flag-icons (MIT). Карта мира: world.geo.json (Unlicense).</sub>
</div>
