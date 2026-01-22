#!/bin/sh

# Fail the script if any command fails
set -e

echo "Starting Xcode Cloud Post-Clone Script..."

# 1. Install Java 17 (Required for Gradle/Kotlin Multiplatform)
echo "Installing OpenJDK 17..."
brew install openjdk@17

# 2. Symlink Java so the system can find it
# This creates a link in the system Java folder pointing to the brew installation
sudo ln -sfn /usr/local/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk

# 3. Export JAVA_HOME just in case (optional but safe)
export JAVA_HOME="/usr/local/opt/openjdk@17"
export PATH="$JAVA_HOME/bin:$PATH"

# 4. Verify Java is accessible
echo "Verifying Java installation:"
java -version

echo "Post-clone setup complete. Ready to build!"