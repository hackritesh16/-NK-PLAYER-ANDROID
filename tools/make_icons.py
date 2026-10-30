#!/usr/bin/env python3
"""Generate Android launcher icons from the NK PLAYER logo PNG.

Source: branding/nk-icon.png, nk-icon.png, or any single square PNG (>=512px) in the repo root.

Run from the repository root:  python3 tools/make_icons.py
Does nothing (exit 0) if no such PNG exists, so the build keeps working.
"""
import os
import sys
from PIL import Image

CANDIDATES = ["branding/nk-icon.png", "nk-icon.png"]
RES = "app/src/main/res"
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
FG_SCALE = 0.70  # logo size inside the 108dp adaptive canvas (keeps it inside the safe zone)

def find_source():
    for path in CANDIDATES:
        if os.path.exists(path):
            return path
    # Fallback: a single large square PNG uploaded to the repository root (e.g. 93902.png).
    for name in sorted(os.listdir(".")):
        if name.lower().endswith(".png") and os.path.isfile(name):
            try:
                with Image.open(name) as im:
                    if im.width == im.height and im.width >= 512:
                        return name
            except Exception:
                pass
    return None


SRC = find_source()
if SRC is None:
    print("[make_icons] no icon PNG found (nk-icon.png) - keeping existing launcher icon")
    sys.exit(0)

src = Image.open(SRC).convert("RGB")
side = min(src.size)
src = src.crop(((src.width - side) // 2, (src.height - side) // 2,
                (src.width + side) // 2, (src.height + side) // 2))

for dpi, px in LEGACY.items():
    folder = os.path.join(RES, f"mipmap-{dpi}")
    os.makedirs(folder, exist_ok=True)
    icon = src.resize((px, px), Image.LANCZOS)
    icon.save(os.path.join(folder, "ic_launcher.png"))
    icon.save(os.path.join(folder, "ic_launcher_round.png"))

for dpi, px in ADAPTIVE.items():
    folder = os.path.join(RES, f"mipmap-{dpi}")
    os.makedirs(folder, exist_ok=True)
    inner = int(px * FG_SCALE)
    logo = src.resize((inner, inner), Image.LANCZOS).convert("RGBA")
    canvas = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    canvas.paste(logo, ((px - inner) // 2, (px - inner) // 2))
    canvas.save(os.path.join(folder, "ic_launcher_fg.png"))

adaptive_dir = os.path.join(RES, "mipmap-anydpi-v26")
os.makedirs(adaptive_dir, exist_ok=True)
xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_fg" />
</adaptive-icon>
'''
for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    with open(os.path.join(adaptive_dir, name), "w") as f:
        f.write(xml)

# The old vector icon in mipmap-anydpi would override the new PNGs on API 24-25.
old = os.path.join(RES, "mipmap-anydpi", "ic_launcher.xml")
if os.path.exists(old):
    os.remove(old)

print("[make_icons] launcher icons generated from", SRC)
