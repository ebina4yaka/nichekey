package dev.example.jpkeyboard

import android.content.Context

/**
 * mozc_dict.bin（tools/build_mozc_dict.py が Mozc OSS辞書から生成）の読込と完全一致検索。
 * 形式: [u32 count][u8 maxReadingLen][data: reading+word 連結][index: count×9B]
 * index entry: [u32 dataOffset][u16 readingLen][u8 wordLen][u16 cost] (big endian)
 */
class MozcDict private constructor(
    private val bytes: ByteArray,
) : Converter.Source {
    private val count = be32(bytes, 0)
    private val maxReadingLen = bytes[4].toInt() and 0xFF
    private val indexStart = bytes.size - count * 9
    private val dataStart = 5 // [u32 count][u8 maxReadingLen] ヘッダー直後が data 先頭

    override fun lookup(reading: String): List<Converter.Word> {
        val p = reading.toByteArray(Charsets.UTF_8)
        if (p.isEmpty() || p.size > maxReadingLen) return emptyList()
        var lo = 0
        var hi = count
        while (lo < hi) { // lower_bound: 最初に reading >= p
            val mid = (lo + hi) ushr 1
            if (cmpReading(mid, p) < 0) lo = mid + 1 else hi = mid
        }
        val out = ArrayList<Converter.Word>()
        var i = lo
        while (i < count && cmpReading(i, p) == 0) {
            val off = be32(bytes, indexStart + i * 9)
            val rl = be16(bytes, indexStart + i * 9 + 4)
            val wl = bytes[indexStart + i * 9 + 6].toInt() and 0xFF
            val cost = be16(bytes, indexStart + i * 9 + 7)
            // data: [u8 readingLen][reading][u8 wordLen][word]（off は data 相対）
            out.add(Converter.Word(String(bytes, dataStart + off + 2 + rl, wl, Charsets.UTF_8), cost))
            i++
        }
        return out
    }

    private fun readingAt(i: Int): ByteArray {
        val off = be32(bytes, indexStart + i * 9)
        val rl = be16(bytes, indexStart + i * 9 + 4)
        return bytes.copyOfRange(dataStart + off + 1, dataStart + off + 1 + rl)
    }

    private fun cmpReading(
        i: Int,
        p: ByteArray,
    ): Int {
        val r = readingAt(i)
        val n = minOf(r.size, p.size)
        for (k in 0 until n) {
            val a = r[k].toInt() and 0xFF
            val b = p[k].toInt() and 0xFF
            if (a != b) return a - b
        }
        return r.size - p.size
    }

    private fun be32(
        b: ByteArray,
        i: Int,
    ) = ((b[i].toInt() and 0xFF) shl 24) or ((b[i + 1].toInt() and 0xFF) shl 16) or
        ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)

    private fun be16(
        b: ByteArray,
        i: Int,
    ) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    companion object {
        @Volatile private var cache: MozcDict? = null

        fun get(context: Context): MozcDict {
            cache?.let { return it }
            synchronized(this) {
                cache?.let { return it }
                val bytes = context.assets.open("mozc_dict.bin").use { it.readBytes() }
                return MozcDict(bytes).also { cache = it }
            }
        }
    }
}
