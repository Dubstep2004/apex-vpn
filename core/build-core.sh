#!/usr/bin/env bash
# Собирает ядро APEX VPN (sing-box libbox + парсер подписки apexcore) в app/libs/libbox.aar
#
# Требуется: Go 1.25+, JDK 17, Android SDK (ANDROID_HOME) и NDK 28 (ANDROID_NDK_HOME).
# Запуск из корня проекта:  ./core/build-core.sh
set -euo pipefail

SING_BOX_VERSION="${SING_BOX_VERSION:-v1.13.21}"
GOMOBILE_VERSION="v0.1.12"
BUILD_TAGS="with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api,badlinkname,tfogo_checklinkname0"
TARGETS="${TARGETS:-android/arm64,android/arm}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/core/.build"
OUT="$ROOT/app/libs/libbox.aar"

: "${ANDROID_HOME:?Укажите ANDROID_HOME (путь к Android SDK)}"
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  ANDROID_NDK_HOME="$(ls -d "$ANDROID_HOME"/ndk/28.* 2>/dev/null | sort -V | tail -1 || true)"
fi
: "${ANDROID_NDK_HOME:?NDK 28 не найден: установите 'ndk;28.0.13004108' через sdkmanager}"
export ANDROID_HOME ANDROID_NDK_HOME

echo "==> sing-box $SING_BOX_VERSION"
rm -rf "$WORK"
git clone --quiet --depth 1 --branch "$SING_BOX_VERSION" https://github.com/SagerNet/sing-box "$WORK"
cp -r "$ROOT/core/apexcore" "$WORK/experimental/apexcore"

echo "==> gomobile $GOMOBILE_VERSION"
go install "github.com/sagernet/gomobile/cmd/gomobile@$GOMOBILE_VERSION"
go install "github.com/sagernet/gomobile/cmd/gobind@$GOMOBILE_VERSION"
export PATH="$(go env GOPATH)/bin:$PATH"

cd "$WORK"
echo "==> тесты парсера подписки"
APEX_RULES_DIR="$ROOT/app/src/main/assets/rules" go test -tags "$BUILD_TAGS" -ldflags "-checklinkname=0" ./experimental/apexcore/

echo "==> gomobile bind ($TARGETS)"
gomobile bind -v \
  -target "$TARGETS" \
  -androidapi 21 \
  -javapkg=io.nekohasekai \
  -libname=box \
  -trimpath -buildvcs=false \
  -ldflags "-X github.com/sagernet/sing-box/constant.Version=${SING_BOX_VERSION#v} -X internal/godebug.defaultGODEBUG=multipathtcp=0 -s -w -buildid= -checklinkname=0" \
  -tags "$BUILD_TAGS" \
  -o libbox.aar \
  ./experimental/libbox ./experimental/apexcore

mkdir -p "$(dirname "$OUT")"
cp libbox.aar "$OUT"
echo "==> готово: $OUT ($(du -h "$OUT" | cut -f1))"
