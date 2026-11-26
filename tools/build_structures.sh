#!/bin/bash
# Rebuilds the structure files from src/gametest/.../StructureBuilder.java and copies them into the mod.
cd "$(dirname "$0")/.."
OMNISPELL_BUILD_STRUCTURES=1 tools/run_client_test.sh || exit 1
found=$(find build/run/clientGameTest/saves -path "*generated/omnispell*" -name "*.nbt" | head -1)
[ -z "$found" ] && { echo "no structure files were saved"; exit 1; }
mkdir -p src/main/resources/data/omnispell/structure
cp "$(dirname "$found")"/*.nbt src/main/resources/data/omnispell/structure/
ls -la src/main/resources/data/omnispell/structure/
