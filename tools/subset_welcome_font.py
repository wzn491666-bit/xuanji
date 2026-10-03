"""Build and verify the OFL welcome font subset from the official upstream font."""

import argparse
import hashlib
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("font", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    official_fonts = {
        "LXGWWenKai-Regular.ttf": "39ad71264b588165b469e35e6afb162a378dacd1f95348160240ba9038ac3009",
        "ZCOOLXiaoWei-Regular.ttf": "a42b620140f493db42f741351dfbf343c0936d58588ee8004b8b2a218d997ff1",
        "MaShanZheng-Regular.ttf": "6d2546bb189c732a8ca29af9e22457b152387d158aa459e4ac2ce1e51788b7fb",
        "NotoSerifSC[wght].ttf": "050080d9255a86808f2945bffac582b31ef32bc36411ce29563b4961670c66f9",
        "ZCOOLQingKeHuangYou-Regular.ttf": "54f0c0df4308cd74cd0f2fd3494ae054dbc4a1fd6fa7d71f4807eb4cdd8b4136",
        "LongCang-Regular.ttf": "e5bf2c3f24ef2327c6f136d8f73e2f9dfdf44896fdbeb35a9515f44777bb91bc",
        "ZhiMangXing-Regular.ttf": "644e0cae9b40f0b10ab729a01bd32032e3973bac22be3dccae01bf6ae7fde969",
        "LiuJianMaoCao-Regular.ttf": "cab396b91a5b7c0b4005a35891180d06e6751f5ac261fe680aec65c1ae209033",
    }
    expected = official_fonts[args.font.name]
    if hashlib.sha256(args.font.read_bytes()).hexdigest() != expected:
        raise ValueError("The font does not match the verified official upstream asset")

    root = Path(__file__).resolve().parents[1]
    source = root / "app/src/main/java/com/xuanji/android/ui/chat/StellarWelcomeScene.kt"
    characters = {ord(c) for c in source.read_text(encoding="utf-8") if c.isprintable()}
    font = TTFont(args.font)
    missing = characters - font.getBestCmap().keys()
    if missing:
        raise ValueError(f"Missing upstream glyphs: {sorted(missing)}")
    options = subset.Options()
    options.name_IDs = ["*"]
    options.name_legacy = True
    options.name_languages = ["*"]
    options.layout_features = ["*"]
    options.notdef_glyph = True
    options.notdef_outline = True
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=characters)
    subsetter.subset(font)
    if "fvar" in font:
        instantiateVariableFont(font, {"wght": 400}, inplace=True)
    family = {"LXGWWenKai-Regular.ttf": "Stellar Verse", "ZCOOLXiaoWei-Regular.ttf": "Stellar Inscription",
              "MaShanZheng-Regular.ttf": "Stellar Brush", "NotoSerifSC[wght].ttf": "Stellar Serif",
              "ZCOOLQingKeHuangYou-Regular.ttf": "Stellar Display", "LongCang-Regular.ttf": "Stellar Flow",
              "ZhiMangXing-Regular.ttf": "Stellar Gesture", "LiuJianMaoCao-Regular.ttf": "Stellar Cursive"}[args.font.name]
    postscript = family.replace(" ", "") + "-Regular"
    names = {1: family, 2: "Regular", 3: postscript + "-subset", 4: family + " Regular",
             6: postscript, 16: family, 17: "Regular"}
    for record in font["name"].names:
        if record.nameID in names:
            record.string = names[record.nameID].encode(record.getEncoding())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    font.save(args.output)
    with TTFont(args.output) as result:
        if characters - result.getBestCmap().keys():
            raise ValueError("Subset lost a required glyph")
    print(f"Verified {len(characters)} characters; subset {args.output.stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
