#!/usr/bin/env python3
"""Copy the Android app's Material Symbols vector drawables into the iOS asset catalog as SVGs.

The iOS screens use the same icons as Android (docs/IOS-REDESIGN-PLAN.md: no SF Symbols where
Android has its own). Re-run after Android adds or changes an `ic_*.xml`:

    python3 -I mobile/iosApp/scripts/convert_android_icons.py

Reads  mobile/androidApp/src/main/res/drawable/ic_*.xml (launcher icons skipped)
Writes mobile/iosApp/iosApp/Design/Icons.xcassets/<name>.imageset/{<name>.svg,Contents.json}
as template images (tinted by SwiftUI's foregroundStyle), vector data preserved.

Only what the drawables use is supported: <vector> viewport/size, <group> translate/scale,
<path> pathData + fillColor (+ fillAlpha / fillType). Anything else fails loudly.
"""
import json
import pathlib
import shutil
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
ROOT = pathlib.Path(__file__).resolve().parents[2]  # mobile/
SRC = ROOT / "androidApp/src/main/res/drawable"
OUT = ROOT / "iosApp/iosApp/Design/Icons.xcassets"
SKIP = {"ic_launcher_background", "ic_launcher_foreground"}


def attr(el, name, default=None):
    return el.get(ANDROID + name, default)


def dp(value):
    return float(value.removesuffix("dp"))


def color(value):
    # #AARRGGBB or #RRGGBB -> (#RRGGBB, alpha)
    v = value.lstrip("#")
    if len(v) == 8:
        return "#" + v[2:], int(v[:2], 16) / 255
    if len(v) == 6:
        return "#" + v, 1.0
    raise ValueError(f"unsupported colour {value}")


def convert_children(el, depth=1):
    out = []
    pad = "  " * depth
    for child in el:
        tag = child.tag
        if tag == "group":
            parts = []
            tx, ty = attr(child, "translateX", "0"), attr(child, "translateY", "0")
            if tx != "0" or ty != "0":
                parts.append(f"translate({tx} {ty})")
            sx, sy = attr(child, "scaleX", "1"), attr(child, "scaleY", "1")
            if sx != "1" or sy != "1":
                parts.append(f"scale({sx} {sy})")
            for unsupported in ("rotation", "pivotX", "pivotY"):
                if attr(child, unsupported) is not None:
                    raise ValueError(f"group {unsupported} not supported")
            transform = f' transform="{" ".join(parts)}"' if parts else ""
            out.append(f"{pad}<g{transform}>")
            out += convert_children(child, depth + 1)
            out.append(f"{pad}</g>")
        elif tag == "path":
            fill, alpha = color(attr(child, "fillColor", "#FF000000"))
            alpha *= float(attr(child, "fillAlpha", "1"))
            extra = f' fill-opacity="{alpha:g}"' if alpha != 1 else ""
            if attr(child, "fillType") == "evenOdd":
                extra += ' fill-rule="evenodd"'
            if attr(child, "strokeColor") is not None:
                raise ValueError("stroked paths not supported")
            out.append(f'{pad}<path d="{attr(child, "pathData")}" fill="{fill}"{extra}/>')
        else:
            raise ValueError(f"unsupported element <{tag}>")
    return out


def to_svg(path):
    root = ET.parse(path).getroot()
    vw, vh = attr(root, "viewportWidth"), attr(root, "viewportHeight")
    w, h = dp(attr(root, "width")), dp(attr(root, "height"))
    body = "\n".join(convert_children(root))
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{w:g}" height="{h:g}" viewBox="0 0 {vw} {vh}">\n'
        f"{body}\n</svg>\n"
    )


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    (OUT / "Contents.json").write_text(json.dumps({"info": {"author": "xcode", "version": 1}}, indent=2) + "\n")
    count = 0
    for xml in sorted(SRC.glob("ic_*.xml")):
        name = xml.stem
        if name in SKIP:
            continue
        imageset = OUT / f"{name}.imageset"
        imageset.mkdir()
        (imageset / f"{name}.svg").write_text(to_svg(xml))
        contents = {
            "images": [{"filename": f"{name}.svg", "idiom": "universal"}],
            "info": {"author": "xcode", "version": 1},
            "properties": {"preserves-vector-representation": True, "template-rendering-intent": "template"},
        }
        (imageset / "Contents.json").write_text(json.dumps(contents, indent=2) + "\n")
        count += 1
    print(f"wrote {count} icons to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    sys.exit(main())
