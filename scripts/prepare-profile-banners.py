import base64
import io
from pathlib import Path
import xml.etree.ElementTree as element_tree

from PIL import Image


SVG_NAMESPACE = "http://www.w3.org/2000/svg"
LEGACY_STARS = {
    "blue-hour": [("m0-48 12 36 36 12-36 12-12 36-12-36-36-12 36-12z", 0, 0, 116)],
    "lemon-paper": [
        ("m850 50 10 43 43 10-43 10-10 43-10-43-43-10 43-10z", 850, 103, 126),
        ("m982 202 7 22 22 7-22 7-7 22-7-22-22-7 22-7z", 982, 231, 69),
    ],
    "star-orbit": [
        ("m900 72 16 49 49 17-49 16-16 50-17-50-49-16 49-17z", 900, 138, 156),
        ("m393 81 6 18 18 6-18 6-6 18-6-18-18-6 18-6z", 393, 105, 58),
    ],
    "comic-panels": [
        ("m717 41 11 34 34 11-34 11-11 34-11-34-34-11 34-11z", 717, 86, 108),
        ("m1060 100 14 44 44 14-44 14-14 44-14-44-44-14 44-14z", 1060, 158, 138),
    ],
    "blue-wave": [("m917 39 12 35 35 12-35 12-12 35-12-35-35-12 35-12z", 917, 86, 112)],
    "little-spark": [
        ("m932 60 15 46 46 15-46 15-15 46-15-46-46-15 46-15z", 932, 121, 146),
        ("m716 184 7 21 21 7-21 7-7 21-7-21-21-7 21-7z", 716, 212, 67),
    ],
}


def main():
    project_root = Path(__file__).resolve().parent.parent
    banner_dir = project_root / "frontend/public/profile-presets/banners"
    with Image.open(project_root / "frontend/public/brand/luminous-star-soft.png") as source:
        # 复用已确认品牌图片，只缩放与编码，不重绘形状、黑边或蓝黄渐变。
        image = source.convert("RGBA").resize((128, 128), Image.Resampling.LANCZOS)
        buffer = io.BytesIO()
        image.save(buffer, "PNG", optimize=True)
    image_url = "data:image/png;base64," + base64.b64encode(buffer.getvalue()).decode("ascii")
    element_tree.register_namespace("", SVG_NAMESPACE)

    for name, stars in LEGACY_STARS.items():
        path = banner_dir / f"{name}.svg"
        root = element_tree.parse(path).getroot()
        parents = {child: parent for parent in root.iter() for child in parent}
        existing = list(root.findall(f".//{{{SVG_NAMESPACE}}}image[@data-luminous-star='true']"))
        if existing:
            for node in existing:
                node.set("href", image_url)
            if len(existing) != len(stars):
                raise ValueError(f"{name}: 品牌星星数量不匹配")
        else:
            for legacy_path, center_x, center_y, size in stars:
                target = next((node for node in root.iter() if node.get("d") == legacy_path), None)
                if target is None:
                    raise ValueError(f"{name}: 未找到待替换的星星")
                parent = parents[target]
                index = list(parent).index(target)
                replacement = element_tree.Element(f"{{{SVG_NAMESPACE}}}image", {
                    "x": str(center_x - size / 2), "y": str(center_y - size / 2),
                    "width": str(size), "height": str(size),
                    "href": image_url, "preserveAspectRatio": "xMidYMid meet",
                    "data-luminous-star": "true",
                })
                parent.remove(target)
                parent.insert(index, replacement)
        if name == "blue-hour":
            for node in list(root):
                if node.get("d") == "m476 63 18 18m-43-2 25 5m1-30 5 25":
                    root.remove(node)
        # 数据内嵌保证作为 img 加载 SVG 时仍能显示品牌图片，无外部依赖。
        element_tree.indent(root, space="  ")
        path.write_text(element_tree.tostring(root, encoding="unicode") + "\n", encoding="utf-8")
        print(f"{name}: {len(stars)} brand stars")


if __name__ == "__main__":
    main()
