#!/bin/sh
set -e

echo "Starting Xcode Cloud Post-Clone Script..."

export HOMEBREW_NO_AUTO_UPDATE=1
export HOMEBREW_NO_ENV_HINTS=1

# Ensure Homebrew is on PATH (Apple Silicon + Intel)
if [ -x /opt/homebrew/bin/brew ]; then
  eval "$(/opt/homebrew/bin/brew shellenv)"
elif [ -x /usr/local/bin/brew ]; then
  eval "$(/usr/local/bin/brew shellenv)"
fi

# 1. Install Java 17
echo "Installing OpenJDK 17..."
brew install openjdk@17

JDK_PREFIX="$(brew --prefix openjdk@17)"
JDK_BUNDLE="${JDK_PREFIX}/libexec/openjdk.jdk"

if [ ! -d "$JDK_BUNDLE" ]; then
  echo "ERROR: OpenJDK bundle not found at $JDK_BUNDLE"
  exit 1
fi

# 2. Create local Java folder (No sudo needed)
mkdir -p "$HOME/Library/Java/JavaVirtualMachines"

# 3. Symlink Java using brew --prefix (works on both /opt/homebrew and /usr/local)
echo "Linking OpenJDK 17 from $JDK_BUNDLE..."
ln -sfn "$JDK_BUNDLE" "$HOME/Library/Java/JavaVirtualMachines/openjdk-17.jdk"

export JAVA_HOME="$JDK_PREFIX"
echo "Java 17 installed. JAVA_HOME=$JAVA_HOME"
java -version
