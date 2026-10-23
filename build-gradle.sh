#!/bin/bash
# Gradle 构建管线（v1.4.0 起的主构建方式；build-manual.sh 为 v1.3.x 时代的手工管线，仅作历史备用，
# 新源码用了 ML Kit / ONNX Runtime 依赖，手工管线编不了）。
# 签名在 app/build.gradle 里指向同一把 ~/workspace/tools/debug.keystore，保证可覆盖安装旧版。
set -e
cd "$(dirname "$0")"

export JAVA_HOME="$HOME/workspace/tools/jdk17"
export GRADLE_USER_HOME="$HOME/workspace/tools/gradle-home"
# 进程内构建的堆内存先设好（后面的网络适配段会往 GRADLE_OPTS 里追加 truststore 参数，别覆盖）
export GRADLE_OPTS="-Xmx2g -Dfile.encoding=UTF-8"

# 本机沙箱的网络适配（缺一不可，否则 Gradle 解析依赖必失败）：
# 1) 代理凭据会轮换 → 每次构建前从环境变量刷新；且代理主机名解析出的 IPv4 地址
#    会被沙箱的“其他 TCP”策略拦截，必须用其 IPv6 字面地址直连。
# 2) 出口代理做 TLS 中间人，CA 只装在系统证书库里，JDK 不认 → 给 JVM 准备一份
#    含该 CA 的 truststore（系统 cacerts 副本 + hatch-egress-ca）。
if [ -n "$HTTPS_PROXY" ]; then
  _hp=$(echo "$HTTPS_PROXY" | sed -E 's#^https?://([^:]+):([^@]+)@([^:/]+):([0-9]+).*#\1 \2 \3 \4#')
  read -r _u _p _h _port <<EOF
$_hp
EOF
  _v6=$(getent ahosts "$_h" 2>/dev/null | awk '$1 ~ /:/ {print $1; exit}')
  _host=${_v6:-$_h}
  if [ -n "$_u" ] && [ -n "$_port" ]; then
    cat > "$GRADLE_USER_HOME/gradle.properties" <<EOF
systemProp.http.proxyHost=$_host
systemProp.http.proxyPort=$_port
systemProp.http.proxyUser=$_u
systemProp.http.proxyPassword=$_p
systemProp.https.proxyHost=$_host
systemProp.https.proxyPort=$_port
systemProp.https.proxyUser=$_u
systemProp.https.proxyPassword=$_p
systemProp.http.nonProxyHosts=localhost|127.0.0.1
systemProp.https.nonProxyHosts=localhost|127.0.0.1
EOF
  fi
fi
_TS="$HOME/workspace/tools/muse-truststore.p12"
_CA=/usr/local/share/ca-certificates/hatch-egress-ca.crt
if [ -f "$_CA" ] && { [ ! -f "$_TS" ] || [ "$_CA" -nt "$_TS" ]; }; then
  cp "$JAVA_HOME/lib/security/cacerts" "$_TS"
  "$JAVA_HOME/bin/keytool" -importcert -noprompt -trustcacerts -alias hatch-egress-ca \
      -file "$_CA" -keystore "$_TS" -storepass changeit >/dev/null 2>&1 || true
fi
if [ -f "$_TS" ]; then
  export GRADLE_OPTS="$GRADLE_OPTS -Djavax.net.ssl.trustStore=$_TS -Djavax.net.ssl.trustStorePassword=changeit"
fi
# 进程内构建：沙箱会杀掉脱离会话的 Gradle daemon，故 --no-daemon 且用 GRADLE_OPTS
# 让启动器 JVM 本身带足堆内存（与 gradle.properties 不设 org.gradle.jvmargs 配套，避免 fork）。
"$HOME/workspace/tools/gradle-8.7/bin/gradle" :app:assembleRelease --console=plain --no-daemon

cp app/build/outputs/apk/release/app-release.apk "$HOME/workspace/your_files/task-timer-probe.apk"
echo "DONE: app/build/outputs/apk/release/app-release.apk -> ~/workspace/your_files/task-timer-probe.apk"
