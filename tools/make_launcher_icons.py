"""Build launcher mipmaps from the supplied karate icon."""

from __future__ import annotations

import sys
from collections import deque
from pathlib import Path

from PIL import Image

MAROON = (137, 1, 15, 255)
RES = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"

LEGACY = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
FOREGROUND = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}


def is_outside(pixel: tuple[int, int, int, int]) -> bool:
    red, green, blue, _alpha = pixel
    # Outer matte is near-white. The figure is enclosed by maroon, so a
    # flood fill from the edges does not reach it.
    return min(red, green, blue) > 170 and abs(red - green) < 40 and abs(green - blue) < 40


def knock_out_matte(image: Image.Image) -> Image.Image:
    image = image.convert("RGBA")
    width, height = image.size
    pixels = image.load()
    seen = bytearray(width * height)
    queue: deque[tuple[int, int]] = deque()

    def push(x: int, y: int) -> None:
        if 0 <= x < width and 0 <= y < height:
            index = y * width + x
            if not seen[index]:
                seen[index] = 1
                queue.append((x, y))

    for x in range(width):
        push(x, 0)
        push(x, height - 1)
    for y in range(height):
        push(0, y)
        push(width - 1, y)

    while queue:
        x, y = queue.popleft()
        if not is_outside(pixels[x, y]):
            continue
        pixels[x, y] = (0, 0, 0, 0)
        push(x + 1, y)
        push(x - 1, y)
        push(x, y + 1)
        push(x, y - 1)
    return image


def content_box(image: Image.Image) -> tuple[int, int, int, int]:
    alpha = image.getchannel("A")
    bbox = alpha.getbbox()
    if bbox is None:
        raise SystemExit("icon has no visible pixels")
    return bbox


def on_maroon(image: Image.Image, size: int, scale: float) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), MAROON)
    target = max(1, int(size * scale))
    fitted = image.resize((target, target), Image.Resampling.LANCZOS)
    offset = (size - target) // 2
    canvas.alpha_composite(fitted, (offset, offset))
    return canvas.convert("RGB")


def main() -> None:
    source = Path(sys.argv[1])
    artwork = knock_out_matte(Image.open(source))
    cropped = artwork.crop(content_box(artwork))

    for folder, size in LEGACY.items():
        destination = RES / folder
        destination.mkdir(parents=True, exist_ok=True)
        icon = on_maroon(cropped, size, 0.92)
        icon.save(destination / "ic_launcher.png", optimize=True)
        icon.save(destination / "ic_launcher_round.png", optimize=True)

    for folder, size in FOREGROUND.items():
        destination = RES / folder
        destination.mkdir(parents=True, exist_ok=True)
        # Adaptive masks crop the outer ring. Keep the figure inside it.
        on_maroon(cropped, size, 0.72).save(
            destination / "ic_launcher_foreground.png",
            optimize=True,
        )


if __name__ == "__main__":
    main()
