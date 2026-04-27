#!/bin/zsh
set -euo pipefail

repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
plugin_dir="$HOME/.runelite/sideloaded-plugins"
java_home="${JAVA_HOME:-/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home}"

cd "$repo_dir"
"$java_home/bin/java" -version >/dev/null
JAVA_HOME="$java_home" ./gradlew test jar

mkdir -p "$plugin_dir"
cp "$repo_dir/build/libs/detect-auto-alchers-1.0-SNAPSHOT.jar" "$plugin_dir/detect-auto-alchers-1.0-SNAPSHOT.jar"

echo "Installed $plugin_dir/detect-auto-alchers-1.0-SNAPSHOT.jar"
