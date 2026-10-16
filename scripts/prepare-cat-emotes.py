from pathlib import Path
from PIL import Image


def main():
    project_root = Path(__file__).resolve().parent.parent
    source_dir = project_root / "assets/characters/emotes-preview-v2-20261013"
    target_dir = project_root / "frontend/public/emotes/cats"
    target_dir.mkdir(parents=True, exist_ok=True)
    for name in ("happy", "like", "laugh", "confused", "sad", "love"):
        # 只缩小和编码已确认的原图，所有设备共用一张带透明通道的素材。
        with Image.open(source_dir / f"{name}.png") as original:
            image = original.convert("RGBA").resize((192, 192), Image.Resampling.LANCZOS)
            destination = target_dir / f"{name}.webp"
            image.save(destination, "WEBP", quality=90, method=6)
            print(f"{name}: {destination.stat().st_size} bytes")


if __name__ == "__main__":
    main()
