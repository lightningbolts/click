#!/bin/sh

# Fail the script if any command fails
set -e

echo "Starting Xcode Cloud Post-Clone Script..."

# 1. Install Java 17 (Required for Gradle/Kotlin Multiplatform)
echo "Installing OpenJDK 17..."
brew install openjdk@17

# 2. Create the local JavaVirtualMachines directory if it doesn't exist
# We use $HOME because we don't have sudo access to the system /Library
mkdir -p "$HOME/Library/Java/JavaVirtualMachines"

# 3. Symlink the installed Java to the user's local JVM folder
# This makes it discoverable by the system Java wrappers without needing sudo
echo "Linking OpenJDK 17 to user library..."
ln -sfn /usr/local/opt/openjdk@17/libexec/openjdk.jdk "$HOME/Library/Java/JavaVirtualMachines/openjdk-17.jdk"

# 4. Verify installation
echo "Verifying Java installation..."
if [ -x "$HOME/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home/bin/java" ]; then
    "$HOME/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home/bin/java" -version
    echo "Java 17 installed and linked successfully."
else
    echo "Error: Java binary not found after linking."
    exit 1
fi