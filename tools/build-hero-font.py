import sys
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parent.parent
FONTS = ROOT / "src/main/resources/assets/battlecraft/font"

HERO_AXES = {"opsz": 32, "wght": 300}
FAMILY = "BattleCraft Hero"
STYLE = "Regular"


def rename(font):
    table = font["name"]
    for record in list(table.names):
        if record.nameID in (1, 3, 4, 6, 16, 17):
            table.removeNames(record.nameID, record.platformID, record.platEncID, record.langID)
    table.setName(FAMILY, 1, 3, 1, 0x409)
    table.setName(STYLE, 2, 3, 1, 0x409)
    table.setName(FAMILY + " " + STYLE, 4, 3, 1, 0x409)
    table.setName("BattleCraftHero-Regular", 6, 3, 1, 0x409)
    table.setName(FAMILY + ";" + STYLE, 3, 3, 1, 0x409)


def build(source, target):
    font = TTFont(source)
    instancer.instantiateVariableFont(font, HERO_AXES, inplace=True, updateFontNames=False)
    rename(font)
    font.save(target)

    built = TTFont(target)
    print("hero glyphs:", len(built.getBestCmap()),
          "upm:", built["head"].unitsPerEm,
          "cap:", built["OS/2"].sCapHeight)


if __name__ == "__main__":
    build(Path(sys.argv[1]), FONTS / "hero.ttf")
