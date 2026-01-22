#!/bin/sh

# Navigate to the project root (up 2 levels from iosApp/ci_scripts)
cd ../..

echo "Generating local.properties..."

# Create the file with dummy values or environment variables
# Note: if your app CRASHES without real keys, you must set 
# MAPS_API_KEY in Xcode Cloud Environment Variables.
cat <<EOF > local.properties
sdk.dir=$HOME/Library/Android/sdk
MAPS_API_KEY=${MAPS_API_KEY:-"dummy_key_for_build"}
SUPABASE_URL=${SUPABASE_URL:-"dummy_url"}
SUPABASE_KEY=${SUPABASE_KEY:-"dummy_key"}
EOF

echo "local.properties created at $(pwd)/local.properties"