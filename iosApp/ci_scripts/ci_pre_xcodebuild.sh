#!/bin/sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Re-export JAVA_HOME for any pre-xcodebuild steps (post-clone wrote this file).
if [ -f "${SCRIPT_DIR}/.java_home" ]; then
  JAVA_HOME="$(cat "${SCRIPT_DIR}/.java_home")"
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
  echo "Using JAVA_HOME=$JAVA_HOME"
fi

# Navigate to the project root (up 2 levels from iosApp/ci_scripts)
cd ../..

echo "Generating local.properties..."

# Create the file with dummy values or environment variables.
# Xcode Cloud / local dev should override with real keys in local.properties.
# When local.properties is absent, Gradle falls back to checked-in local.defaults.properties.
cat <<EOF > local.properties
sdk.dir=$HOME/Library/Android/sdk
MAPS_API_KEY=${MAPS_API_KEY:-"dummy_key_for_build"}
SUPABASE_URL=${SUPABASE_URL:-"dummy_url"}
SUPABASE_KEY=${SUPABASE_KEY:-"dummy_key"}
EOF

echo "local.properties created at $(pwd)/local.properties"