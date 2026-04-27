#!/bin/zsh
set -euo pipefail

runelite_repo="$HOME/.runelite/repository2"
java_home="${JAVA_HOME:-/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home}"

latest_jar() {
  local pattern="$1"
  local jar
  jar="$(find "$runelite_repo" -maxdepth 1 -type f -name "$pattern" -print | sort | tail -n 1)"
  if [[ -z "$jar" ]]; then
    echo "Unable to find $pattern in $runelite_repo" >&2
    exit 1
  fi
  echo "$jar"
}

client_jar="$(latest_jar 'client-*.jar')"
injected_client_jar="$(latest_jar 'injected-client-*.jar')"
api_jar="$(latest_jar 'runelite-api-*-runtime.jar')"

classpath="$client_jar:$injected_client_jar:$api_jar"
for jar in "$runelite_repo"/*.jar; do
  base="$(basename "$jar")"
  case "$base" in
    client-*.jar|injected-client-*.jar|runelite-api-*-runtime.jar)
      continue
      ;;
  esac
  classpath="$classpath:$jar"
done

exec "$java_home/bin/java" \
  -Xmx768m \
  -Xss2m \
  --add-opens=java.base/java.net=ALL-UNNAMED \
  --add-opens=java.base/java.io=ALL-UNNAMED \
  --add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED \
  -Dsun.java2d.metal=false \
  -Dsun.java2d.opengl=true \
  -Dapple.awt.application.appearance=system \
  -cp "$classpath" \
  net.runelite.client.RuneLite \
  --developer-mode \
  "$@"
