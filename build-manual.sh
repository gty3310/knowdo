#!/bin/bash
# 手动构建管线（与时习同法：SDK build-tools 直连，产物为 debug 签名可安装 APK）
set -e
cd "$(dirname "$0")"

if [ -d "$HOME/workspace/tools/jdk17/bin" ]; then
  export PATH="$HOME/workspace/tools/jdk17/bin:$PATH"
fi

SDK=${ANDROID_HOME:-$HOME/workspace/tools/android-sdk}
BT=$SDK/build-tools/34.0.0
AJAR=$SDK/platforms/android-34/android.jar
SRC=app/src/main
B=build/manual
rm -rf $B
mkdir -p $B/gen $B/classes $B/dex
mkdir -p $SRC/assets

echo "[1/6] aapt2 compile"
$BT/aapt2 compile --dir $SRC/res -o $B/compiled.zip

echo "[2/6] aapt2 link"
$BT/aapt2 link -o $B/base.apk -I $AJAR \
  --manifest $SRC/AndroidManifest.xml \
  --java $B/gen \
  -A $SRC/assets \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 19 --version-name 1.3.6 \
  $B/compiled.zip

echo "[3/6] javac"
find $SRC/java $B/gen -name '*.java' > $B/sources.txt
javac --release 8 -encoding UTF-8 -cp $AJAR -d $B/classes @$B/sources.txt

echo "[4/6] d8"
(cd $B/classes && jar cf ../classes.jar .)
$BT/d8 --min-api 26 --lib $AJAR --output $B/dex $B/classes.jar

echo "[5/6] package + zipalign"
cp $B/base.apk $B/unsigned.apk
(cd $B/dex && zip -q ../unsigned.apk classes.dex)
$BT/zipalign -f -p 4 $B/unsigned.apk $B/aligned.apk

echo "[6/6] sign (debug key)"
KS=$HOME/workspace/tools/debug.keystore
$BT/apksigner sign --ks "$KS" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out $B/task-timer.apk $B/aligned.apk

cp $B/task-timer.apk $HOME/workspace/your_files/task-timer-probe.apk
echo "DONE: $B/task-timer.apk"
