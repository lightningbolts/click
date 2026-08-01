#!/usr/bin/env python3
"""Render the circle/square SVG logo into Android mipmaps, iOS AppIcon, and KMP loading drawable."""
from __future__ import annotations

import shutil
import subprocess
from pathlib import Path

from PIL import Image

try:
    _LANCZOS = Image.Resampling.LANCZOS
except AttributeError:
    _LANCZOS = Image.LANCZOS

ROOT = Path(__file__).resolve().parents[1]
DESIGN_DIR = ROOT / "docs" / "design-assets"
ICON_SVG = DESIGN_DIR / "circle-square-logo-icon.svg"
MARK_SVG = DESIGN_DIR / "circle-square-logo-mark.svg"

IOS_APPICON_DIR = ROOT / "iosApp" / "iosApp" / "Assets.xcassets" / "AppIcon.appiconset"
IOS_EXTRA_APPICON_DIRS = [
    ROOT / "iosApp" / "ClickClip" / "Assets.xcassets" / "AppIcon.appiconset",
    ROOT / "iosApp" / "NotificationService" / "Assets.xcassets" / "AppIcon.appiconset",
]
SOURCE_LOGO_PATH = IOS_APPICON_DIR / "ClickLogo2.png"

ANDROID_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Adaptive-icon foreground canvas is 108dp; content should stay in the center ~66%.
FOREGROUND_SIZES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}

COMPOSE_LOADING_LOGO = (
    ROOT / "composeApp" / "src" / "commonMain" / "composeResources" / "drawable" / "click_logo.png"
)
COMPOSE_LOADING_MAX_SIDE = 1280


def render_svg(svg_path: Path, size: int, dest: Path) -> None:
    if not svg_path.is_file():
        raise SystemExit(f"Missing SVG: {svg_path}")
    dest.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            "rsvg-convert",
            "-w",
            str(size),
            "-h",
            str(size),
            str(svg_path),
            "-o",
            str(dest),
        ],
        check=True,
    )


def resize_square(src: Image.Image, size: int) -> Image.Image:
    return src.resize((size, size), _LANCZOS)


def make_adaptive_foreground(icon: Image.Image, size: int) -> Image.Image:
    """Inset the mark into the adaptive-icon safe zone on a transparent canvas."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    content = int(size * 0.66)
    mark = resize_square(icon, content)
    canvas.paste(mark, ((size - content) // 2, (size - content) // 2), mark)
    return canvas


def export_compose_loading_logo() -> None:
    tmp = COMPOSE_LOADING_LOGO.with_suffix(".tmp.png")
    render_svg(MARK_SVG, COMPOSE_LOADING_MAX_SIDE, tmp)
    img = Image.open(tmp).convert("RGBA")
    # Tight-crop transparent mark, then pad into a square.
    alpha = img.split()[3]
    bbox = alpha.getbbox()
    if bbox is None:
        raise SystemExit("Rendered mark SVG was empty")
    cropped = img.crop(bbox)
    pad = 48
    w, h = cropped.size
    side = max(w, h) + pad * 2
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(cropped, ((side - w) // 2, (side - h) // 2), cropped)
    if side > COMPOSE_LOADING_MAX_SIDE:
        square = square.resize((COMPOSE_LOADING_MAX_SIDE, COMPOSE_LOADING_MAX_SIDE), _LANCZOS)
    COMPOSE_LOADING_LOGO.parent.mkdir(parents=True, exist_ok=True)
    square.save(COMPOSE_LOADING_LOGO, format="PNG", compress_level=3)
    tmp.unlink(missing_ok=True)
    print("Wrote", COMPOSE_LOADING_LOGO, square.size)


def main() -> None:
    # Canonical 1024 source for iOS + script consumers
    IOS_APPICON_DIR.mkdir(parents=True, exist_ok=True)
    render_svg(ICON_SVG, 1024, SOURCE_LOGO_PATH)
    print("Wrote", SOURCE_LOGO_PATH)

    out_1024 = IOS_APPICON_DIR / "app-icon-1024.png"
    shutil.copy2(SOURCE_LOGO_PATH, out_1024)
    print("Wrote", out_1024)

    for extra in IOS_EXTRA_APPICON_DIRS:
        if not extra.is_dir():
            continue
        dest = extra / "app-icon-1024.png"
        shutil.copy2(SOURCE_LOGO_PATH, dest)
        print("Wrote", dest)

    logo = Image.open(SOURCE_LOGO_PATH).convert("RGBA")
    res = ROOT / "composeApp" / "src" / "androidMain" / "res"
    for folder, dim in ANDROID_SIZES.items():
        im = resize_square(logo, dim)
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            out = res / folder / name
            out.parent.mkdir(parents=True, exist_ok=True)
            im.save(out, "PNG")
            print("Wrote", out)

    mark_tmp = res / "mipmap-xxxhdpi" / "_mark_source.png"
    render_svg(MARK_SVG, 1024, mark_tmp)
    mark = Image.open(mark_tmp).convert("RGBA")
    for folder, dim in FOREGROUND_SIZES.items():
        out = res / folder / "ic_launcher_foreground.png"
        make_adaptive_foreground(mark, dim).save(out, "PNG")
        print("Wrote", out)
    mark_tmp.unlink(missing_ok=True)

    export_compose_loading_logo()


if __name__ == "__main__":
    main()
