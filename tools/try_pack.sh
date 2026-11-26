#!/bin/bash
# usage: tools/try_pack.sh <log name> [jar name fragments to leave out...]
# Boots the game with pack/mods minus the named jars and runs the world generation test.
cd "$(dirname "$0")/.."
log=$1; shift
rm -rf pack/try && mkdir -p pack/try
for f in pack/mods/*.jar; do
  skip=0
  for frag in "$@"; do case "$(basename "$f" | tr 'A-Z' 'a-z')" in *"$frag"*) skip=1;; esac; done
  [ $skip -eq 0 ] && ln -s "$(realpath "$f")" pack/try/
done
echo "trying $(ls pack/try | wc -l) jars (left out: $*)"
OMNISPELL_PACK_DIR=pack/try OMNISPELL_WORLDGEN=1 timeout 590 tools/run_client_test.sh > "$log" 2>&1
echo "rc=$?"
pkill -f "fabric.client.gametest"
grep -E "Loading [0-9]+ mods|omnispell-test\]|FAILED|AssertionError|BUILD|Incompatible| requires |Could not execute entrypoint|Minecraft has crashed|Caused by: |/ERROR\]" "$log" | grep -v "ALSA\|UnknownHost\|authlib\|OpenAL\|backend OpenGL\|Anisotropic\|SoundSystem\|BackendCreation" | sed 's/.*omnispell-test\] //' | cut -c1-260 | head -${LINES_SHOWN:-30}
