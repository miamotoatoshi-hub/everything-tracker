#!/usr/bin/env python3
"""Turns raw app screenshots into Google Play phone screenshots (1080x1920, 9:16) with a caption.

Usage: frame_screenshots.py <input_dir> <output_dir>
Input files are the `store_<lang>_light_NN_name.png` screenshots printed by the CI screenshot tour.
Requires Pillow and a font with Latin + Cyrillic (Inter or DejaVu Sans).
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1080, 1920
TOP = (99, 91, 255)       # brand indigo
BOTTOM = (61, 52, 176)

CAPTIONS = {
    "en": {
        "01_home": "Build habits that stick",
        "03_details": "Streaks that keep you going",
        "06_calendar": "See every day at a glance",
        "07_stats": "Statistics that show real progress",
        "09_weekly": "Your week in one view",
        "09b_achievements": "Unlock 19 achievements",
        "13_form": "Track anything: water, reading, sport",
        "plans": "Unlock everything with Premium",
    },
    "ru": {
        "01_home": "Привычки, которые остаются с вами",
        "03_details": "Серии, которые мотивируют",
        "06_calendar": "Каждый день как на ладони",
        "07_stats": "Статистика реального прогресса",
        "09_weekly": "Вся неделя на одном экране",
        "09b_achievements": "Собери 19 достижений",
        "13_form": "Вода, чтение, спорт — что угодно",
        "plans": "Всё и сразу с Premium",
    },
}

FONT_CANDIDATES = [
    "/usr/share/fonts/opentype/inter/Inter-Bold.otf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]


def font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def gradient():
    img = Image.new("RGB", (W, H), TOP)
    draw = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        color = tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3))
        draw.line([(0, y), (W, y)], fill=color)
    return img


def wrap(draw, text, fnt, max_width):
    words, lines, line = text.split(), [], ""
    for word in words:
        candidate = (line + " " + word).strip()
        if draw.textlength(candidate, font=fnt) <= max_width:
            line = candidate
        else:
            lines.append(line)
            line = word
    lines.append(line)
    return lines


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1], radius, fill=255)
    out = Image.new("RGBA", img.size)
    out.paste(img, (0, 0), mask)
    return out


def frame(src_path, caption, out_path):
    canvas = gradient().convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    fnt = font(76)
    lines = wrap(draw, caption, fnt, W - 160)
    y = 110
    for line in lines:
        w = draw.textlength(line, font=fnt)
        draw.text(((W - w) / 2, y), line, font=fnt, fill="white")
        y += 96
    shot = Image.open(src_path).convert("RGB")
    target_h = H - (y + 60)
    scale = target_h / shot.height
    shot = shot.resize((int(shot.width * scale), target_h), Image.LANCZOS)
    phone = rounded(shot, 48)
    x = (W - phone.width) // 2
    top = y + 60
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [x + 6, top + 18, x + phone.width + 6, top + phone.height + 18], 48, fill=(0, 0, 0, 90))
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(24)))
    canvas.paste(phone, (x, top), phone)
    canvas.convert("RGB").save(out_path, "PNG", optimize=True)


def main(src_dir, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    made = 0
    for name in sorted(os.listdir(src_dir)):
        if not (name.startswith("store_") and name.endswith(".png")):
            continue
        stem = name[len("store_"):-4]            # en_light_01_home | plans_light
        if stem.startswith("plans_"):
            continue
        lang, _, key = stem.split("_", 2)
        caption = CAPTIONS.get(lang, {}).get(key)
        if caption is None:
            continue
        frame(os.path.join(src_dir, name), caption, os.path.join(out_dir, f"{lang}_{key}.png"))
        made += 1
    print(f"{made} screenshots written to {out_dir}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
