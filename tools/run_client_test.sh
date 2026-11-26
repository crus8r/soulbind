#!/bin/bash
# Runs the automated play-through in a virtual display (Linux, no monitor needed).
# Needs: xvfb, mesa-vulkan-drivers. Screenshots land in build/run/clientGameTest/screenshots.
cd "$(dirname "$0")/.."
export LIBGL_ALWAYS_SOFTWARE=1
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-$(mktemp -d)}"
exec xvfb-run -a -s "-screen 0 1280x720x24 +extension GLX +render" ./gradlew runClientGameTest --no-daemon --max-workers=2 "$@"
