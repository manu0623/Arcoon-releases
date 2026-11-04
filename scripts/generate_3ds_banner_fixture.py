import argparse
import binascii
import struct
import subprocess
import tempfile
import wave
import zlib
from pathlib import Path


WIDTH = 256
HEIGHT = 128
REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = (
    REPO_ROOT
    / "app"
    / "src"
    / "test"
    / "resources"
    / "com"
    / "nendo"
    / "argosy"
    / "data"
    / "media"
    / "argosy-banner-fixture.bnr"
)


def inside_triangle(x, y, a, b, c):
    def cross(p, q):
        return (q[0] - p[0]) * (y - p[1]) - (q[1] - p[1]) * (x - p[0])

    values = (cross(a, b), cross(b, c), cross(c, a))
    return all(value >= 0 for value in values) or all(value <= 0 for value in values)


def pixel(x, y):
    blend = (x + y) / (WIDTH + HEIGHT)
    color = (
        int(14 + 15 * blend),
        int(24 + 20 * blend),
        int(48 + 28 * blend),
    )
    if inside_triangle(x, y, (52, 94), (108, 26), (108, 94)):
        color = (245, 180, 71)
    if inside_triangle(x, y, (119, 94), (166, 42), (212, 94)):
        color = (70, 195, 207)
    if 20 <= x < 236 and 101 <= y < 111:
        color = (230, 238, 242)
    if 20 <= x < 84 and 115 <= y < 121:
        color = (70, 195, 207)
    if 88 <= x < 152 and 115 <= y < 121:
        color = (245, 180, 71)
    if 156 <= x < 220 and 115 <= y < 121:
        color = (230, 238, 242)
    return color


def png_chunk(kind, data):
    body = kind + data
    return struct.pack(">I", len(data)) + body + struct.pack(
        ">I", binascii.crc32(body) & 0xFFFFFFFF
    )


def write_artwork(path):
    rows = bytearray()
    for y in range(HEIGHT):
        rows.append(0)
        for x in range(WIDTH):
            rows.extend(pixel(x, y))

    image = (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(
            b"IHDR",
            struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 2, 0, 0, 0),
        )
        + png_chunk(b"IDAT", zlib.compress(rows, level=9))
        + png_chunk(b"IEND", b"")
    )
    path.write_bytes(image)


def write_silence(path):
    with wave.open(str(path), "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(22050)
        audio.writeframes(bytes(22050 * 2))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--bannertool", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    if not args.bannertool.is_file():
        parser.error(f"bannertool executable not found: {args.bannertool}")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="argosy-3ds-banner-") as temp_dir:
        artwork = Path(temp_dir) / "argosy-banner.png"
        silence = Path(temp_dir) / "silence.wav"
        write_artwork(artwork)
        write_silence(silence)
        subprocess.run(
            [
                str(args.bannertool),
                "makebanner",
                "-i",
                str(artwork),
                "-a",
                str(silence),
                "-o",
                str(args.output.resolve()),
            ],
            check=True,
        )


if __name__ == "__main__":
    main()
