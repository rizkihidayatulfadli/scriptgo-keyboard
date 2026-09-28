"""Converts the Urbanist woff2 files to TTF for Android (res/font accepts only TTF/OTF).

Usage, from the repo root, after `npm pack @fontsource/urbanist@5.3.0` and extracting it
to /tmp/fontsource:  python3 design/fonts/convert.py /tmp/fontsource/package/files
Needs: pip install fonttools brotli
"""
import sys
from pathlib import Path

from fontTools.ttLib import TTFont

WEIGHTS = {400: "regular", 500: "medium", 600: "semibold", 700: "bold", 800: "extrabold"}
OUT = Path("android/app/src/main/res/font")


def main(files: Path) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for weight, name in WEIGHTS.items():
        font = TTFont(files / f"urbanist-latin-{weight}-normal.woff2")
        font.flavor = None
        target = OUT / f"urbanist_{name}.ttf"
        font.save(target)
        print(target)


if __name__ == "__main__":
    main(Path(sys.argv[1]))
