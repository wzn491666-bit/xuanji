"""Render the actual bundled welcome font subsets as an offline typography proof."""

import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


def main():
    root = Path(__file__).resolve().parents[1]
    source = (root / "app/src/main/java/com/xuanji/android/ui/chat/StellarWelcomeScene.kt").read_text(encoding="utf-8")
    scenes = re.findall(r'StellarWelcomeScene\("([^"\n]+)", "([^"\n]+)", "([^"\n]+)", "([^"\n]+)", (0x[0-9A-F]+), WelcomeComposition\.(\w+), WelcomeTypeface\.(\w+)\)', source)
    if len(scenes) != 16:
        raise ValueError("Expected all 16 scenes")
    sheet = Image.new("RGB", (1600, 1320), "white")
    draw = ImageDraw.Draw(sheet)
    regular = ImageFont.truetype(str(root / "app/build/visual-checks/fonts/LXGWWenKai-Regular.ttf"), 19)
    draw.text((40, 22), "Offline font proof. Not an Android screenshot.", fill="#68727e", font=regular)
    for index, (_, domain, first, second, accent, composition, typeface) in enumerate(scenes):
        x, y = (index % 4) * 400, 64 + (index // 4) * 308
        ink = f"#{int(accent, 16) & 0xffffff:06x}"
        draw.text((x + 200, y + 36), f"星笺 · {domain}", fill="#69717b", font=regular, anchor="mm")
        font = ImageFont.truetype(str(root / f"app/src/main/res/font/stellar_{typeface.lower()}.ttf"), 30 if composition != "QUIET" else 27)
        if composition == "OFFSET":
            draw.text((x + 74, y + 90), first, fill="#232832", font=font)
            draw.text((x + 326, y + 159), second, fill=ink, font=font, anchor="ra")
        else:
            draw.text((x + 200, y + 103), first, fill="#232832", font=font, anchor="mm")
            if composition == "QUIET":
                draw.line((x + 186, y + 135, x + 214, y + 135), fill=ink, width=1)
            draw.text((x + 200, y + 161), second, fill=ink, font=font, anchor="mm")
        draw.text((x + 200, y + 232), typeface, fill="#949ba4", font=regular, anchor="mm")
        draw.line((x + 32, y + 285, x + 368, y + 285), fill="#ecedef", width=1)
    output = root / "output/imagegen/stellar-typography-proof.png"
    output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(output)
    print(f"Rendered all 16 scenes with bundled fonts: {output}")


if __name__ == "__main__":
    main()
