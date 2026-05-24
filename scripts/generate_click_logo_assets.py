#!/usr/bin/env python3
"""Resize ClickLogo2.png from iOS AppIcon assets into Android mipmaps, app-icon-1024, and KMP loading drawable."""
from __future__ import annotations

from pathlib import Path

import numpy as np
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


COMPOSE_LOADING_LOGO = (
    ROOT / "composeApp" / "src" / "commonMain" / "composeResources" / "drawable" / "click_logo.png"
)
COMPOSE_LOADING_MAX_SIDE = 1280


def export_compose_loading_logo(src: Image.Image) -> None:
    """High-res transparent PNG for AppShimmerScreen — always from app ClickLogo2, not web assets."""
    data = np.array(src.convert("RGBA"), dtype=np.float32)
    r, g, b = data[:, :, 0], data[:, :, 1], data[:, :, 2]
    white = (r >= 248) & (g >= 248) & (b >= 248)
    near_white = (r >= 228) & (g >= 228) & (b >= 228) & ~white
    data[white, 3] = 0
    data[near_white, 3] = np.minimum(data[near_white, 3], 48)
    img = Image.fromarray(data.astype(np.uint8), "RGBA")

    alpha = np.array(img)[:, :, 3]
    ys, xs = np.where(alpha > 24)
    cropped = img.crop((int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1))

    pad = 24
    w, h = cropped.size
    side = max(w, h) + pad * 2
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(cropped, ((side - w) // 2, (side - h) // 2), cropped)
    if side > COMPOSE_LOADING_MAX_SIDE:
        square = square.resize((COMPOSE_LOADING_MAX_SIDE, COMPOSE_LOADING_MAX_SIDE), _LANCZOS)

    COMPOSE_LOADING_LOGO.parent.mkdir(parents=True, exist_ok=True)
    square.save(COMPOSE_LOADING_LOGO, format="PNG", compress_level=3)
    print("Wrote", COMPOSE_LOADING_LOGO, square.size)


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

    export_compose_loading_logo(Image.open(SOURCE_LOGO_PATH))


if __name__ == "__main__":
    main()
