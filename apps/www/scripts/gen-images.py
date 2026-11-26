"""Pre-generates responsive WebP variants for every image in public/{images,product,schools}.

Run after adding or replacing an image:  python scripts/gen-images.py
Variants are written next to the original as <name>-<width>.webp and served as static files by
the custom next/image loader (src/lib/image-loader.ts), so no image is resized at request time.
"""
import os
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "public")
WIDTHS = [320, 480, 828, 1200, 1600]

for folder in ("images", "product", "schools"):
    d = os.path.join(ROOT, folder)
    for f in sorted(os.listdir(d)):
        base, ext = os.path.splitext(f)
        if ext != ".webp" or base.rsplit("-", 1)[-1].isdigit():
            continue
        im = Image.open(os.path.join(d, f)).convert("RGB")
        for w in WIDTHS:
            out = os.path.join(d, f"{base}-{w}.webp")
            v = im if w >= im.width else im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
            v.save(out, "WEBP", quality=72, method=6)
        print(folder, f, im.size)
