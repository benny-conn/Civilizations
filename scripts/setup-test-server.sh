#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
server_dir="$repo_root/server"
gradle_properties="$repo_root/gradle.properties"

read_gradle_property() {
    awk -F= -v key="$1" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$gradle_properties"
}

paper_version="$(read_gradle_property paperMinecraftVersion)"
paper_build="$(read_gradle_property paperBuild)"
paper_url="$(read_gradle_property paperDownloadUrl)"
paper_sha256="$(read_gradle_property paperDownloadSha256)"

if [[ -z "$paper_version" || -z "$paper_build" || -z "$paper_url" || -z "$paper_sha256" ]]; then
    echo "Paper test-server properties are missing from gradle.properties." >&2
    exit 1
fi

sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{ print $1 }'
    else
        shasum -a 256 "$1" | awk '{ print $1 }'
    fi
}

mkdir -p "$server_dir/plugins"

paper_jar="$server_dir/paper.jar"
if [[ ! -f "$paper_jar" || "$(sha256 "$paper_jar")" != "$paper_sha256" ]]; then
    temporary_jar="$server_dir/paper.jar.download"
    echo "Downloading Paper $paper_version build $paper_build..."
    curl --fail --location --retry 3 "$paper_url" --output "$temporary_jar"

    actual_sha256="$(sha256 "$temporary_jar")"
    if [[ "$actual_sha256" != "$paper_sha256" ]]; then
        rm -f "$temporary_jar"
        echo "Paper checksum mismatch: expected $paper_sha256, received $actual_sha256." >&2
        exit 1
    fi

    mv "$temporary_jar" "$paper_jar"
fi

if [[ ! -f "$server_dir/eula.txt" ]]; then
    printf '%s\n' \
        '# By running this development server you agree to the Minecraft EULA (https://aka.ms/MinecraftEULA).' \
        'eula=true' > "$server_dir/eula.txt"
fi

if [[ ! -f "$server_dir/server.properties" ]]; then
    printf '%s\n' \
        'motd=Civilizations development server' \
        'online-mode=true' \
        'server-port=25565' \
        'max-players=10' \
        'spawn-protection=0' \
        'view-distance=8' \
        'simulation-distance=6' \
        'enable-query=false' \
        'enable-rcon=false' > "$server_dir/server.properties"
fi

echo "Test server is ready at $server_dir."
