import re
import sys
from pathlib import Path

from fontTools import merge, subset
from fontTools.ttLib import TTFont
from fontTools.ttLib.scaleUpem import scale_upem
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parent.parent
FONTS = ROOT / "src/main/resources/assets/battlecraft/font"
CHARSET = ROOT / "tools/charset.txt"

DISPLAY_AXES = {"wght": 900, "wdth": 125}
FAMILY = "BattleCraft Title"
STYLE = "Regular"


def wanted():
    codes = set()
    for line in CHARSET.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        span = re.fullmatch(r"\[(0x[0-9A-Fa-f]+),\s*(0x[0-9A-Fa-f]+)\]", line)
        if span:
            codes.update(range(int(span.group(1), 16), int(span.group(2), 16) + 1))
        elif re.fullmatch(r"0x[0-9A-Fa-f]+", line):
            codes.add(int(line, 16))
    return codes


def display(source):
    font = TTFont(source)
    instancer.instantiateVariableFont(font, DISPLAY_AXES, inplace=True, updateFontNames=False)
    return font


def filler(source, codes):
    font = TTFont(source)
    scale_upem(font, 1000)
    options = subset.Options(layout_features=[], notdef_outline=True, glyph_names=True)
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=codes)
    subsetter.subset(font)
    return font


def rename(font):
    table = font["name"]
    for record in list(table.names):
        if record.nameID in (1, 3, 4, 6, 16, 17):
            table.removeNames(record.nameID, record.platformID, record.platEncID, record.langID)
    table.setName(FAMILY, 1, 3, 1, 0x409)
    table.setName(STYLE, 2, 3, 1, 0x409)
    table.setName(FAMILY + " " + STYLE, 4, 3, 1, 0x409)
    table.setName("BattleCraftTitle-Regular", 6, 3, 1, 0x409)
    table.setName(FAMILY + ";" + STYLE, 3, 3, 1, 0x409)


def build(display_source, filler_source, target):
    codes = wanted()
    shaped = display(display_source)
    missing = sorted(codes - set(shaped.getBestCmap()))

    shaped_path = target.with_suffix(".display.ttf")
    filler_path = target.with_suffix(".filler.ttf")
    shaped.save(shaped_path)
    filler(filler_source, missing).save(filler_path)

    merged = merge.Merger().merge([str(shaped_path), str(filler_path)])
    rename(merged)
    merged.save(target)
    shaped_path.unlink()
    filler_path.unlink()

    covered = set(TTFont(target).getBestCmap())
    print("title glyphs:", len(covered), "missing from charset:", len(codes - covered))


if __name__ == "__main__":
    build(Path(sys.argv[1]), FONTS / "ui_bold.ttf", FONTS / "title.ttf")
