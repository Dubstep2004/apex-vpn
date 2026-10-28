#!/usr/bin/env bash
# Скачивает официальное ядро Xray (github.com/XTLS/Xray-core) в APK:
#   app/src/main/jniLibs/arm64-v8a/libxray.so    ← Xray-android-arm64-v8a.zip
#   app/src/main/jniLibs/armeabi-v7a/libxray.so  ← Xray-linux-arm32-v7a.zip
# Android разрешает запускать файлы только из папки нативных библиотек, отсюда имя lib*.so.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VER="${XRAY_VERSION:-$(cat "$ROOT/core/xray-version")}"
BASE="https://github.com/XTLS/Xray-core/releases/download/$VER"
TMP="$(mktemp -d)"

fetch() {
  local asset="$1" abi="$2"
  curl -fsSL --retry 3 -o "$TMP/$asset" "$BASE/$asset"
  rm -rf "$TMP/x" && mkdir -p "$TMP/x" && unzip -q "$TMP/$asset" xray -d "$TMP/x"
  mkdir -p "$ROOT/app/src/main/jniLibs/$abi"
  install -m 755 "$TMP/x/xray" "$ROOT/app/src/main/jniLibs/$abi/libxray.so"
  echo "==> Xray $VER → $abi ($(du -h "$ROOT/app/src/main/jniLibs/$abi/libxray.so" | cut -f1))"
}

fetch Xray-android-arm64-v8a.zip arm64-v8a
fetch Xray-linux-arm32-v7a.zip armeabi-v7a

# Для проверки конфигов на сборочной машине
if [[ "${XRAY_HOST:-0}" == "1" ]]; then
  curl -fsSL --retry 3 -o "$TMP/host.zip" "$BASE/Xray-linux-64.zip"
  mkdir -p "$ROOT/core/.xray-host" && unzip -qo "$TMP/host.zip" xray -d "$ROOT/core/.xray-host"
  chmod +x "$ROOT/core/.xray-host/xray"
fi
