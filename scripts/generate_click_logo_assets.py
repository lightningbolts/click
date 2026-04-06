#!/usr/bin/env python3
"""Resize ClickLogo2.png from iOS AppIcon assets into Android mipmaps and app-icon-1024.png."""
from __future__ import annotations

from pathlib import Path

from PIL import Image

try:
    _LANCZOS = Image.Resampling.LANCZOS
except AttributeError:
    _LANCZOS = Image.LANCZOS

ROOT = Path(__file__).resolve().parents[1]
IOS_APPICON_DIR = ROOT / "iosApp" / "iosApp" / "Assets.xcassets" / "AppIcon.appiconset"
SOURCE_LOGO_PATH = IOS_APPICON_DIR / "ClickLogo2.png"

# Android mipmap sizes (square launcher)
ANDROID_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def load_source_logo() -> Image.Image:
    if not SOURCE_LOGO_PATH.is_file():
        raise SystemExit(f"Missing source logo: {SOURCE_LOGO_PATH}")
    return Image.open(SOURCE_LOGO_PATH).convert("RGBA")


def resize_square(src: Image.Image, size: int) -> Image.Image:
    return src.resize((size, size), _LANCZOS)


def main() -> None:
    logo = load_source_logo()
    print("Using", SOURCE_LOGO_PATH)

    res = ROOT / "composeApp" / "src" / "androidMain" / "res"
    for folder, dim in ANDROID_SIZES.items():
        im = resize_square(logo, dim)
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            out = res / folder / name
            out.parent.mkdir(parents=True, exist_ok=True)
            im.save(out, "PNG")
            print("Wrote", out)

    ios_dir = IOS_APPICON_DIR
    ios_dir.mkdir(parents=True, exist_ok=True)
    out_1024 = ios_dir / "app-icon-1024.png"
    resize_square(logo, 1024).save(out_1024, "PNG")
    print("Wrote", out_1024)


if __name__ == "__main__":
    main()
