#!/usr/bin/env python3
"""third_party/mozc/dictionary*.txt + connection_single_column.txt → app/src/main/assets/mozc_dict.bin

バイナリ形式 v2 (big endian):
  header: u32 count, u8 maxReadingLen, u16 posSize
  data: count × [u8 readingLen][reading UTF-8][u8 wordLen][word UTF-8]  (reading でソート済み)
  index: count × [u32 dataOffset][u16 readingLen][u8 wordLen][u16 cost][u16 leftId][u16 rightId]
  matrix: posSize × posSize × u16  (row = rid_of_left_node, col = lid_of_right_node)
    遷移コスト: matrix[前単語.rightId][次単語.leftId]
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
U16_MAX = 65535


def kata2hira(s: str) -> str:
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in s)


def load_connection():
    """connection_single_column.txt → (posSize, bytearray[u16] row-major matrix)"""
    path = SRC / "connection_single_column.txt"
    try:
        lines = open(path, encoding="utf-8").read().split("\n")
    except OSError as e:
        sys.exit(f"cannot read {path}: {e}")
    try:
        pos_size = int(lines[0])
    except ValueError as e:
        sys.exit(f"invalid connection header: {e}")
    need = pos_size * pos_size
    values = lines[1:1 + need]
    if len(values) < need:
        sys.exit(f"connection matrix too short: {len(values)} < {need}")
    matrix = bytearray(need * 2)
    for i, v in enumerate(values):
        try:
            cost = int(v)
        except ValueError as e:
            sys.exit(f"invalid connection value at line {i + 2}: {e}")
        rid, lid = divmod(i, pos_size)
        if rid == 0 and lid == 0:
            cost = 0  # gen_connection_data.py と同じ特例 (BOS→EOS)
        struct.pack_into(">H", matrix, i * 2, min(cost, U16_MAX))
    return pos_size, matrix


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
                reading = kata2hira(parts[0])
                left_id, right_id, cost = int(parts[1]), int(parts[2]), int(parts[3])
                word = parts[4]
            except (IndexError, ValueError) as e:
                print(f"skip malformed line in {f}: {e}", file=sys.stderr)
                continue
            if not HIRA.match(reading) or len(reading) > MAX_READING:
                continue
            if not word or len(word) > MAX_WORD or "\t" in word:
                continue
            key = (reading, word)
            if key not in entries or cost < entries[key][2]:
                entries[key] = (left_id, right_id, cost)

    pos_size, matrix = load_connection()

    items = sorted(entries.items(), key=lambda kv: (kv[0][0].encode("utf-8"), kv[0][1]))
    data = bytearray()
    index = bytearray()
    max_rl = 0
    for (reading, word), (left_id, right_id, cost) in items:
        rb = reading.encode("utf-8"); wb = word.encode("utf-8")
        max_rl = max(max_rl, len(rb))
        off = len(data)
        data += struct.pack("B", len(rb)) + rb + struct.pack("B", len(wb)) + wb
        index += struct.pack(
            ">IHBHHH", off, len(rb), len(wb), min(cost, U16_MAX),
            min(left_id, U16_MAX), min(right_id, U16_MAX),
        )
    OUT.parent.mkdir(parents=True, exist_ok=True)
    try:
        with open(OUT, "wb") as fp:
            fp.write(struct.pack(">IBH", len(items), min(max_rl, 255), pos_size))
            fp.write(data)
            fp.write(index)
            fp.write(matrix)
    except OSError as e:
        sys.exit(f"cannot write {OUT}: {e}")
    print(
        f"entries={len(items)} maxReadingBytes={max_rl} posSize={pos_size} "
        f"size={OUT.stat().st_size/1e6:.1f}MB"
    )


if __name__ == "__main__":
    main()
