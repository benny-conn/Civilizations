#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
server_dir="$repo_root/server"

"$script_dir/setup-test-server.sh"
"$repo_root/gradlew" -p "$repo_root" deployTestServerPlugin

java_major_version() {
    "$1" -version 2>&1 | awk -F'[".]' '/version/ { print $2; exit }'
}

java_executable=""

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]] &&
    [[ "$(java_major_version "$JAVA_HOME/bin/java")" -ge 25 ]]; then
    java_executable="$JAVA_HOME/bin/java"
elif [[ -x /opt/homebrew/opt/openjdk/bin/java ]] &&
    [[ "$(java_major_version /opt/homebrew/opt/openjdk/bin/java)" -ge 25 ]]; then
    java_executable="/opt/homebrew/opt/openjdk/bin/java"
elif command -v java >/dev/null 2>&1 &&
    [[ "$(java_major_version "$(command -v java)")" -ge 25 ]]; then
    java_executable="$(command -v java)"
fi

if [[ -z "$java_executable" ]]; then
    echo "Java 25 or newer is required to run Paper 26.2." >&2
    echo "Set JAVA_HOME to a Java 25 JDK, then run this script again." >&2
    exit 1
fi

server_min_memory="${SERVER_MIN_MEMORY:-1G}"
server_max_memory="${SERVER_MAX_MEMORY:-2G}"

cd "$server_dir"
exec "$java_executable" \
    "-Xms$server_min_memory" \
    "-Xmx$server_max_memory" \
    -jar paper.jar \
    --nogui
