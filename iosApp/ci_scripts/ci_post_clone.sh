#!/bin/sh
set -e

echo "Starting Xcode Cloud Post-Clone Script..."

# 1. Install Java 17
echo "Installing OpenJDK 17..."
brew install openjdk@17

# 2. Create local Java folder (No sudo needed)
mkdir -p "$HOME/Library/Java/JavaVirtualMachines"

# 3. Symlink Java (No sudo needed)
echo "Linking OpenJDK 17..."
ln -sfn /usr/local/opt/openjdk@17/libexec/openjdk.jdk "$HOME/Library/Java/JavaVirtualMachines/openjdk-17.jdk"

echo "Java 17 installed."