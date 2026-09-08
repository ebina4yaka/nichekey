#!/usr/bin/env python3
"""third_party/mozc/dictionary*.txt → app/src/main/assets/mozc_dict.bin

バイナリ形式 (big endian):
  u32 count, u8 maxReadingLen
  data: count × [u8 readingLen][reading UTF-8][u8 wordLen][word UTF-8]  (reading でソート済み)
  index: count × [u32 dataOffset][u16 readingLen][u8 wordLen][u16 cost]
"""
import glob
import re
import struct
import sys
from pathlib import Path

SRC = Path(__file__).parent.parent / "third_party" / "mozc"
OUT = Path(__file__).parent.parent / "app" / "src" / "main" / "assets" / "mozc_dict.bin"

HIRA = re.compile(r"^[ぁ-ゖー]+$")
MAX_READING = 20
MAX_WORD = 40

def kata2hira(s: str) -> str:
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in s)

def main():
    entries = {}
    files = sorted(glob.glob(str(SRC / "dictionary*.txt")))
    if not files:
        sys.exit(f"no dictionary TSVs under {SRC}")
    for f in files:
        try:
            lines = open(f, encoding="utf-8").read().splitlines()
        except OSError as e:
            sys.exit(f"cannot read {f}: {e}")
        for line in lines:
            parts = line.split("\t")
            if len(parts) < 5:
                continue
            try:
                reading, cost, word = parts[0], int(parts[3]), parts[4]
            except (IndexError, ValueError) as e:
                print(f"skip malformed line in {f}: {e}", file=sys.stderr)
                continue
            reading = kata2hira(reading)
            if not HIRA.match(reading) or len(reading) > MAX_READING:
                continue
            if not word or len(word) > MAX_WORD or "\t" in word:
                continue
            key = (reading, word)
            if key not in entries or cost < entries[key]:
                entries[key] = cost
    items = sorted(entries.items(), key=lambda kv: (kv[0][0].encode("utf-8"), kv[0][1]))
    data = bytearray()
    index = bytearray()
    max_rl = 0
    for (reading, word), cost in items:
        rb = reading.encode("utf-8"); wb = word.encode("utf-8")
        max_rl = max(max_rl, len(rb))
        off = len(data)
        data += struct.pack("B", len(rb)) + rb + struct.pack("B", len(wb)) + wb
        index += struct.pack(">IHBH", off, len(rb), len(wb), min(cost, 65535))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    try:
        with open(OUT, "wb") as fp:
            fp.write(struct.pack(">IB", len(items), min(max_rl, 255)))
            fp.write(data)
            fp.write(index)
    except OSError as e:
        sys.exit(f"cannot write {OUT}: {e}")
    print(f"entries={len(items)} maxReadingBytes={max_rl} size={OUT.stat().st_size/1e6:.1f}MB")

if __name__ == "__main__":
    main()
