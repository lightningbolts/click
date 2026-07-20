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

# Pin a known Temurin 17.0.19 build (macOS archive uses Contents/Home).
# Avoid Homebrew: openjdk@17 pulls ~29 bottles and Xcode Cloud often fails mid-download.
TEMURIN_VERSION="17.0.19_10"
TEMURIN_TAG="jdk-17.0.19%2B10"

resolve_java_home_from_extracted() {
  extracted="$1"
  if [ -x "${extracted}/Contents/Home/bin/java" ]; then
    echo "${extracted}/Contents/Home"
  elif [ -x "${extracted}/bin/java" ]; then
    echo "${extracted}"
  else
    return 1
  fi
}

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
  tmp_dir="$(mktemp -d)"
  # Direct GitHub release URL (more reliable than Homebrew ghcr bottles).
  url="https://github.com/adoptium/temurin17-binaries/releases/download/${TEMURIN_TAG}/OpenJDK17U-jdk_${adoptium_arch}_mac_hotspot_${TEMURIN_VERSION}.tar.gz"

  curl -fL --retry 3 --retry-delay 2 -o "${tmp_dir}/jdk.tar.gz" "$url"
  tar -xzf "${tmp_dir}/jdk.tar.gz" -C "$tmp_dir"

  extracted=""
  for candidate in "$tmp_dir"/jdk-17* "$tmp_dir"/temurin-17*; do
    if [ -d "$candidate" ]; then
      extracted="$candidate"
      break
    fi
  done

  if [ -z "$extracted" ]; then
    echo "ERROR: Temurin archive did not contain an expected JDK directory"
    rm -rf "$tmp_dir"
    exit 1
  fi

  home="$(resolve_java_home_from_extracted "$extracted")" || {
    echo "ERROR: Failed to locate bin/java under $extracted (expected Contents/Home on macOS)"
    rm -rf "$tmp_dir"
    exit 1
  }

  rm -rf "$JAVA_HOME_PATH"
  mkdir -p "$JDK_ROOT"
  # Move only the Home tree so JAVA_HOME points at a normal JDK root.
  mv "$home" "$JAVA_HOME_PATH"
  rm -rf "$tmp_dir"
}

install_temurin_17

export JAVA_HOME="$JAVA_HOME_PATH"
export PATH="$JAVA_HOME/bin:$PATH"

# Persist for the Compile Kotlin Framework build phase (fresh shell).
echo "$JAVA_HOME" > "${SCRIPT_DIR}/.java_home"

echo "Java 17 installed. JAVA_HOME=$JAVA_HOME"
java -version
