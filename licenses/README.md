# Third-party assets

Every typeface shipped in the jar is licensed under the SIL Open Font License 1.1.

| File in the jar | Source | Licence |
| --- | --- | --- |
| `assets/battlecraft/font/ui.ttf`, `ui_semibold.ttf`, `ui_bold.ttf` | [Inter](https://github.com/rsms/inter) | [Inter-OFL.txt](Inter-OFL.txt) |
| `assets/battlecraft/font/title.ttf` (Latin) | [Archivo](https://github.com/Omnibus-Type/Archivo), instanced at `wght=900 wdth=125` | [Archivo-OFL.txt](Archivo-OFL.txt) |
| `assets/battlecraft/font/title.ttf` (Cyrillic and the rest) | Inter Bold, scaled to a 1000 unit em | [Inter-OFL.txt](Inter-OFL.txt) |
| `assets/battlecraft/font/hero.ttf` | [Inter](https://github.com/rsms/inter), instanced at `opsz=32 wght=300` | [Inter-OFL.txt](Inter-OFL.txt) |

`title.ttf` is built by `tools/build-title-font.py` from a downloaded Archivo variable font and the shipped
Inter Bold; `hero.ttf` by `tools/build-hero-font.py` from a downloaded Inter variable font. The MSDF atlases
beside them come from `tools/generate-font-atlas.sh`.
