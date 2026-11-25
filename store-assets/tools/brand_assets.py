#!/usr/bin/env python3
"""Renders the Trackly store graphics from the vector mark and app screenshots.

Usage: brand_assets.py <raw_screenshots_dir>
  raw_screenshots_dir: the full-resolution `store_<lang>_light_NN_name.png` files from the CI screenshot tour
  (kept in store-assets/raw/).
Writes:
  store-assets/ic_launcher_512.png               Google Play app icon (512x512)
  store-assets/feature_graphic_1024x500.png      Google Play feature graphic (English)
  store-assets/feature_graphic_1024x500_ru.png   Russian feature graphic (set it on the Russian listing)
  website/assets/icon-512.png, icon-192.png, favicon-32.png
Requires: Pillow, cairosvg, the Inter font (falls back to DejaVu Sans).
"""
import io
import os
import sys

import cairosvg
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
BRAND = os.path.join(ROOT, "store-assets", "brand")
TOP, BOTTOM = (124, 124, 248), (58, 45, 191)  # same gradient as the launcher icon

TAGLINES = {
    "en": ("Trackly", "Build habits that stick.", "Streaks · Reminders · Statistics · Widget"),
    "ru": ("Trackly", "Привычки, которые остаются.", "Серии · Напоминания · Статистика · Виджет"),
}
FONTS = {
    "bold": ["/usr/share/fonts/opentype/inter/Inter-Bold.otf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"],
    "medium": ["/usr/share/fonts/opentype/inter/Inter-Medium.otf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"],
}


def font(kind, size):
    for path in FONTS[kind]:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def svg(path, size):
    png = cairosvg.svg2png(url=path, output_width=size, output_height=size)
    return Image.open(io.BytesIO(png)).convert("RGBA")


def gradient(w, h):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = (x / w + y / h) / 2
            px[x, y] = tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3))
    return img.convert("RGBA")


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1], radius, fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def phone(screenshot_path, height):
    """A screenshot as a phone screen: rounded corners and a thin dark bezel."""
    shot = Image.open(screenshot_path).convert("RGBA")
    width = int(shot.width * height / shot.height)
    shot = shot.resize((width, height), Image.LANCZOS)
    bezel = 10
    body = Image.new("RGBA", (width + 2 * bezel, height + 2 * bezel), (20, 20, 32, 255))
    body = rounded(body, 44)
    body.paste(rounded(shot, 36), (bezel, bezel), rounded(shot, 36))
    return body


def paste_with_shadow(canvas, item, xy, blur=18, offset=(0, 14), alpha=110):
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    mask = item.split()[3].point(lambda a: alpha if a > 0 else 0)
    shadow.paste((0, 0, 0, alpha), (xy[0] + offset[0], xy[1] + offset[1]), mask)
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(blur)))
    canvas.alpha_composite(item, xy)


def feature_graphic(lang, raw_dir, out_path):
    W, H = 1024, 500
    canvas = gradient(W, H)
    draw = ImageDraw.Draw(canvas)
    name, tagline, features = TAGLINES[lang]

    icon = rounded(svg(os.path.join(BRAND, "icon.svg"), 96), 24)
    paste_with_shadow(canvas, icon, (64, 120), blur=10, offset=(0, 6), alpha=70)
    draw.text((64, 236), name, font=font("bold", 76), fill="white")
    draw.text((66, 330), tagline, font=font("medium", 30), fill=(255, 255, 255, 235))
    draw.text((66, 378), features, font=font("medium", 20), fill=(255, 255, 255, 190))

    shots = [os.path.join(raw_dir, f"store_{lang}_light_{n}.png") for n in ("07_stats", "01_home")]
    if all(os.path.exists(p) for p in shots):
        back, front = phone(shots[0], 470), phone(shots[1], 500)
        paste_with_shadow(canvas, back, (W - back.width - 22, 70))
        paste_with_shadow(canvas, front, (W - back.width - front.width + 70, 40))
    canvas.convert("RGB").save(out_path, "PNG", optimize=True)


def main(raw_dir):
    store = os.path.join(ROOT, "store-assets")
    web = os.path.join(ROOT, "website", "assets")
    os.makedirs(web, exist_ok=True)
    icon_svg = os.path.join(BRAND, "icon.svg")
    svg(icon_svg, 512).convert("RGB").save(os.path.join(store, "ic_launcher_512.png"), "PNG", optimize=True)
    for size, name in ((512, "icon-512.png"), (192, "icon-192.png"), (32, "favicon-32.png")):
        rounded(svg(icon_svg, size), int(size * 0.22)).save(os.path.join(web, name), "PNG", optimize=True)
    feature_graphic("en", raw_dir, os.path.join(store, "feature_graphic_1024x500.png"))
    feature_graphic("ru", raw_dir, os.path.join(store, "feature_graphic_1024x500_ru.png"))
    print("brand assets written")


if __name__ == "__main__":
    main(sys.argv[1])
