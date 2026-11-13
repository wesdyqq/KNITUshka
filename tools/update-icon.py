#!/usr/bin/env python3
"""Derive launcher and documentation assets from the approved two-tone PNG.

Requires Pillow. Usage: python tools/update-icon.py '/path/to/source.png'
Without an argument, uses the checked-in docs/assets/icon.png.
"""
from pathlib import Path
import sys

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "docs/assets/icon.png"
RES = ROOT / "android/app/src/main/res"
image = Image.open(SOURCE).convert("RGBA")

# Preserve the approved image (including its transparent rounded corners) in the UI and README.
doc = image.resize((512, 512), Image.Resampling.LANCZOS)
doc.save(ROOT / "docs/assets/icon.png", optimize=True)
(RES / "drawable-nodpi").mkdir(exist_ok=True)
image.resize((256, 256), Image.Resampling.LANCZOS).save(
    RES / "drawable-nodpi/knitushka_mark.webp", lossless=True
)
for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    image.resize((size, size), Image.Resampling.LANCZOS).save(
        RES / f"mipmap-{density}/ic_knitushka.webp", lossless=True
    )

# The adaptive icon needs an independent foreground, not a second rounded-square background.
# Unmatte the cream rabbit against its red background; keep the original silhouette and details.
foreground = Image.new("RGBA", image.size)
pixels = []
background = (140, 3, 16)
source_pixels = getattr(image, "get_flattened_data", image.getdata)()
for red, green, blue, opacity in source_pixels:
    # The rabbit is fully opaque in the source. Ignore the rounded background's translucent rim.
    coverage = max(0.0, min(1.0, (green - 8) / 220)) if opacity >= 250 else 0.0
    if coverage < 0.02:
        pixels.append((0, 0, 0, 0))
    else:
        color = tuple(round(max(0, min(255, (c - (1 - coverage) * bg) / coverage)))
                      for c, bg in zip((red, green, blue), background))
        pixels.append((*color, round(opacity * coverage)))
foreground.putdata(pixels)
box = foreground.getbbox()
if box is None:
    raise ValueError("No cream rabbit silhouette found in the two-tone source image")
artwork = foreground.crop(box)
# Keep identifying features inside the adaptive mask instead of letting a circular launcher crop ears.
artwork.thumbnail((248, 248), Image.Resampling.LANCZOS)  # 62dp within a 108dp, 4x canvas
layer = Image.new("RGBA", (432, 432))
layer.alpha_composite(artwork, ((432 - artwork.width) // 2, (432 - artwork.height) // 2))
layer.save(RES / "drawable-nodpi/knitushka_launcher_foreground.png", optimize=True)
monochrome = Image.new("RGBA", layer.size, (0, 0, 0, 0))
monochrome.putalpha(layer.getchannel("A"))
monochrome.save(RES / "drawable-nodpi/knitushka_launcher_monochrome.png", optimize=True)
print("Updated README image, in-app mark, five launcher densities, adaptive and themed icons.")
