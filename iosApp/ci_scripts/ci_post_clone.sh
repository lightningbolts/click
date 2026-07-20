#!/bin/sh
set -e

echo "Starting Xcode Cloud Post-Clone Script..."

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Prefer DerivedData so later builds can reuse the JDK when available.
if [ -n "${CI_DERIVED_DATA_PATH:-}" ]; then
  JDK_ROOT="${CI_DERIVED_DATA_PATH}/JDK"
else
  JDK_ROOT="${HOME}/.click-ci-jdk"
fi

JAVA_HOME_PATH="${JDK_ROOT}/Home"

install_temurin_17() {
  if [ -x "${JAVA_HOME_PATH}/bin/java" ]; then
    echo "JDK already present at ${JAVA_HOME_PATH}"
    return 0
  fi

  arch="$(uname -m)"
  case "$arch" in
    arm64) adoptium_arch="aarch64" ;;
    x86_64) adoptium_arch="x64" ;;
    *)
      echo "ERROR: Unsupported architecture: $arch"
      exit 1
      ;;
  esac

  echo "Downloading Temurin JDK 17 (${adoptium_arch})..."
  # Avoid Homebrew: openjdk@17 pulls ~29 bottles (libxcb, cairo, …) and
  # Xcode Cloud often fails mid-download with "Connection reset by peer".
  tmp_dir="$(mktemp -d)"
  url="https://api.adoptium.net/v3/binary/latest/17/ga/mac/${adoptium_arch}/jdk/hotspot/normal/eclipse?project=jdk"

  curl -fL --retry 3 --retry-delay 2 -o "${tmp_dir}/jdk.tar.gz" "$url"
  tar -xzf "${tmp_dir}/jdk.tar.gz" -C "$tmp_dir"

  extracted="$(find "$tmp_dir" -maxdepth 1 -type d \( -name 'jdk-17*' -o -name 'temurin-17*' \) | head -1)"
  if [ -z "$extracted" ] || [ ! -x "${extracted}/bin/java" ]; then
    echo "ERROR: Failed to extract a usable JDK from Temurin archive"
    rm -rf "$tmp_dir"
    exit 1
  fi

  rm -rf "$JAVA_HOME_PATH"
  mkdir -p "$JDK_ROOT"
  mv "$extracted" "$JAVA_HOME_PATH"
  rm -rf "$tmp_dir"
}

install_temurin_17

export JAVA_HOME="$JAVA_HOME_PATH"
export PATH="$JAVA_HOME/bin:$PATH"

# Persist for the Compile Kotlin Framework build phase (fresh shell).
echo "$JAVA_HOME" > "${SCRIPT_DIR}/.java_home"

echo "Java 17 installed. JAVA_HOME=$JAVA_HOME"
java -version
