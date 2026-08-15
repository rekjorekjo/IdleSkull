#!/usr/bin/env python3
"""Resize/compress IdleSkull theme PNGs while preserving transparency.

Examples:
  python3 tools/optimize_skull_assets.py
  python3 tools/optimize_skull_assets.py --max-size 768 --colors 192 --in-place

Pillow is required: python3 -m pip install pillow
"""

from __future__ import annotations

import argparse
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "app/src/main/res/drawable-nodpi"
PATTERN = "skull_theme_*.png"


def human_size(value: int) -> str:
    units = ("B", "KB", "MB")
    size = float(value)
    for unit in units:
        if size < 1024.0 or unit == units[-1]:
            return f"{size:.1f}{unit}"
        size /= 1024.0
    return f"{value}B"


def optimize(src: Path, dst: Path, max_size: int, colors: int) -> tuple[int, int, tuple[int, int], tuple[int, int]]:
    before = src.stat().st_size
    with Image.open(src) as opened:
        image = opened.convert("RGBA")
        original_size = image.size
        image.thumbnail((max_size, max_size), Image.Resampling.LANCZOS)
        resized = image.size

        # Generated theme art is grayscale-heavy; a palette PNG usually cuts file size a lot
        # without an obvious visual loss at the 320dp display size. Set --colors 0 to skip it.
        if colors > 0:
            image = image.quantize(
                colors=colors,
                method=Image.Quantize.FASTOCTREE,
                dither=Image.Dither.NONE,
            )

        dst.parent.mkdir(parents=True, exist_ok=True)
        temp = dst.with_suffix(dst.suffix + ".tmp")
        image.save(temp, format="PNG", optimize=True, compress_level=9)
        temp.replace(dst)

    after = dst.stat().st_size
    return before, after, original_size, resized


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--max-size", type=int, default=768, help="maximum width/height in pixels (default: 768)")
    parser.add_argument("--colors", type=int, default=192, help="palette colors; 0 disables quantization (default: 192)")
    parser.add_argument("--in-place", action="store_true", help="replace files in drawable-nodpi")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=ROOT / "build/optimized-skulls",
        help="preview output directory when --in-place is not used",
    )
    args = parser.parse_args()

    if args.max_size < 128:
        raise SystemExit("--max-size is too small; use at least 128")
    if args.colors != 0 and not (16 <= args.colors <= 256):
        raise SystemExit("--colors must be 0 or between 16 and 256")

    sources = sorted(ASSET_DIR.glob(PATTERN))
    if not sources:
        raise SystemExit(f"No {PATTERN} files found in {ASSET_DIR}")

    total_before = 0
    total_after = 0
    for src in sources:
        dst = src if args.in_place else args.output_dir / src.name
        before, after, original_size, resized = optimize(src, dst, args.max_size, args.colors)
        total_before += before
        total_after += after
        ratio = 100.0 * after / before if before else 0.0
        print(
            f"{src.name}: {original_size[0]}x{original_size[1]} -> "
            f"{resized[0]}x{resized[1]}, {human_size(before)} -> {human_size(after)} ({ratio:.1f}%)"
        )

    ratio = 100.0 * total_after / total_before if total_before else 0.0
    print(f"TOTAL: {human_size(total_before)} -> {human_size(total_after)} ({ratio:.1f}%)")
    if not args.in_place:
        print(f"Preview files written to: {args.output_dir}")
        print("If they look good, rerun with --in-place.")


if __name__ == "__main__":
    main()
