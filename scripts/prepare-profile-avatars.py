import json
from pathlib import Path

from PIL import Image


def main():
    project_root = Path(__file__).resolve().parent.parent
    source_dir = project_root / "assets/characters/avatar-cos-v1"
    target_dir = project_root / "frontend/public/profile-presets/avatars"
    target_dir.mkdir(parents=True, exist_ok=True)
    catalog = json.loads((source_dir / "approved-v1.json").read_text(encoding="utf-8"))
    for avatar in catalog["avatars"]:
        # 只缩放和编码已确认的原图，保留完整方形构图，圆形展示由界面完成。
        with Image.open(source_dir / avatar["image"]) as original:
            image = original.convert("RGB").resize((320, 320), Image.Resampling.LANCZOS)
            destination = target_dir / f'{avatar["id"]}.webp'
            image.save(destination, "WEBP", quality=90, method=6)
            print(f'{avatar["id"]}: {destination.stat().st_size} bytes')


if __name__ == "__main__":
    main()
