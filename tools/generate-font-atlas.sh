#!/usr/bin/env bash
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
generator="$here/msdf-atlas-gen/msdf-atlas-gen.exe"
charset="$here/charset.txt"
assets="$here/../src/main/resources/assets/battlecraft"
fonts="$assets/font"

mkdir -p "$assets/msdf"

bake() {
    local source="$1"
    local name="$2"

    "$generator" \
        -font "$source" \
        -charset "$charset" \
        -type msdf \
        -format png \
        -size 48 \
        -pxrange 6 \
        -pots \
        -imageout "$assets/msdf/msdf_$name.png" \
        -json "$assets/msdf/msdf_$name.json"

    echo "baked $name"
}

bake "$fonts/ui.ttf" "regular"
bake "$fonts/ui_semibold.ttf" "semibold"
bake "$fonts/ui_bold.ttf" "bold"
bake "$fonts/title.ttf" "title"
bake "$fonts/hero.ttf" "hero"
