"""Generate Android launcher icons from assets/icon.png into the Capacitor android project."""
import os, sys
from PIL import Image, ImageDraw

SRC = "assets/icon.png"
RES = "android/app/src/main/res"
BG_COLOR = "#8E2F6E"  # fills behind the icon on adaptive-icon launchers

legacy = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
foreground = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

if not os.path.isdir(RES):
    sys.exit("Run this from the project root after `npx cap add android`.")

src = Image.open(SRC).convert("RGBA")

def resized(size):
    return src.resize((size, size), Image.LANCZOS)

def round_mask(img):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, img.size[0] - 1, img.size[1] - 1), fill=255)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out

for d, size in legacy.items():
    folder = f"{RES}/mipmap-{d}"
    os.makedirs(folder, exist_ok=True)
    img = resized(size)
    img.save(f"{folder}/ic_launcher.png")
    round_mask(img).save(f"{folder}/ic_launcher_round.png")

for d, size in foreground.items():
    folder = f"{RES}/mipmap-{d}"
    os.makedirs(folder, exist_ok=True)
    resized(size).save(f"{folder}/ic_launcher_foreground.png")

# background colour used by the adaptive icon
values = f"{RES}/values"
os.makedirs(values, exist_ok=True)
with open(f"{values}/ic_launcher_background.xml", "w") as f:
    f.write(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <color name="ic_launcher_background">{BG_COLOR}</color>\n</resources>\n'
    )
print("Launcher icons generated.")
