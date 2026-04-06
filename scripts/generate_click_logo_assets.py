#!/usr/bin/env python3
"""Generate ClickLogo2.png and Android/iOS launcher bitmaps from a simple vector-style layout."""
from __future__ import annotations

import math
import os
import struct
import zlib
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
LOGO_PATH = ROOT / "ClickLogo2.png"

# Android mipmap sizes (square launcher)
ANDROID_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def draw_logo(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    m = size / 1024.0
    r = int(180 * m)

    def lerp_color(c1, c2, t):
        return tuple(int(a + (b - a) * t) for a, b in zip(c1, c2))

    # Background gradient (approximate with vertical bands)
    top_left = (135, 206, 250)
    top_right = (75, 0, 130)
    bottom = (138, 43, 226)
    for y in range(size):
        ty = y / max(size - 1, 1)
        left = lerp_color(top_left, bottom, ty)
        right = lerp_color(top_right, bottom, ty)
        for x in range(size):
            tx = x / max(size - 1, 1)
            c = tuple(int(left[i] * (1 - tx) + right[i] * tx) for i in range(3))
            img.putpixel((x, y), c + (255,))

    # Rounded rect mask
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle((0, 0, size - 1, size - 1), radius=r, fill=255)
    bg = img.copy()
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    img.paste(bg, mask=mask)

    draw = ImageDraw.Draw(img)
    w = int(72 * m)
    # White chevron + bar (simplified)
    pts_bar = [
        (int(220 * m), int(780 * m)),
        (int(780 * m), int(220 * m)),
        (int(780 * m), int(220 * m) + w),
        (int(220 * m) + w * 2, int(780 * m)),
    ]
    draw.polygon(pts_bar, fill=(255, 255, 255, 255))
    chev_w = int(100 * m)
    pts_chev = [
        (size // 2 - chev_w, int(820 * m)),
        (size // 2, int(720 * m)),
        (size // 2 + chev_w, int(820 * m)),
        (size // 2 + chev_w - int(40 * m), int(820 * m) + chev_w // 2),
        (size // 2, int(780 * m)),
        (size // 2 - chev_w + int(40 * m), int(820 * m) + chev_w // 2),
    ]
    draw.polygon(pts_chev, fill=(255, 255, 255, 255))
    return img


def write_png_rgba(path: Path, pixels: list[tuple[int, int, int, int]], w: int, h: int) -> None:
    raw = b"".join(struct.pack(">I", (a << 24) | (r << 16) | (g << 8) | b) for r, g, b, a in pixels)
    zlib_obj = zlib.compressobj(9, zlib.DEFLATED, -zlib.MAX_WBITS)
    compressed = zlib_obj.compress(raw) + zlib_obj.flush()
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", compressed) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def main() -> None:
    logo = draw_logo(1024)
    LOGO_PATH.parent.mkdir(parents=True, exist_ok=True)
    logo.save(LOGO_PATH, "PNG")
    print("Wrote", LOGO_PATH)

    res = ROOT / "composeApp" / "src" / "androidMain" / "res"
    for folder, dim in ANDROID_SIZES.items():
        im = draw_logo(dim)
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            out = res / folder / name
            im.save(out, "PNG")
            print("Wrote", out)

    ios_dir = ROOT / "iosApp" / "iosApp" / "Assets.xcassets" / "AppIcon.appiconset"
    ios_dir.mkdir(parents=True, exist_ok=True)
    logo.save(ios_dir / "app-icon-1024.png", "PNG")
    print("Wrote", ios_dir / "app-icon-1024.png")


if __name__ == "__main__":
    main()
