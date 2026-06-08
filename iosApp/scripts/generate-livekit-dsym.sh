#!/bin/sh
set -e

FRAMEWORK_BINARY="${BUILT_PRODUCTS_DIR}/${FRAMEWORKS_FOLDER_PATH}/LiveKitWebRTC.framework/LiveKitWebRTC"
DSYM_OUTPUT="${DWARF_DSYM_FOLDER_PATH}/LiveKitWebRTC.framework.dSYM"

if [ ! -f "$FRAMEWORK_BINARY" ]; then
  exit 0
fi

if [ -d "$DSYM_OUTPUT" ]; then
  exit 0
fi

echo "Generating dSYM for LiveKitWebRTC.framework"
/usr/bin/dsymutil "$FRAMEWORK_BINARY" -o "$DSYM_OUTPUT"
