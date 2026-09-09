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
    private val maxReadingLen = bytes[MAX_READING_LEN_POS].toInt() and BYTE_MASK
    private val indexStart = bytes.size - count * ENTRY_SIZE
    private val dataStart = HEADER_SIZE // [u32 count][u8 maxReadingLen] ヘッダー直後が data 先頭

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
            val entry = indexStart + i * ENTRY_SIZE
            val off = be32(bytes, entry)
            val rl = be16(bytes, entry + IDX_READING_LEN)
            val wl = bytes[entry + IDX_WORD_LEN].toInt() and BYTE_MASK
            val cost = be16(bytes, entry + IDX_COST)
            // data: [u8 readingLen][reading][u8 wordLen][word]（off は data 相対）
            out.add(Converter.Word(String(bytes, dataStart + off + DATA_HEADER + rl, wl, Charsets.UTF_8), cost))
            i++
        }
        return out
    }

    private fun readingAt(i: Int): ByteArray {
        val entry = indexStart + i * ENTRY_SIZE
        val off = be32(bytes, entry)
        val rl = be16(bytes, entry + IDX_READING_LEN)
        return bytes.copyOfRange(dataStart + off + 1, dataStart + off + 1 + rl)
    }

    private fun cmpReading(
        i: Int,
        p: ByteArray,
    ): Int {
        val r = readingAt(i)
        val n = minOf(r.size, p.size)
        for (k in 0 until n) {
            val a = r[k].toInt() and BYTE_MASK
            val b = p[k].toInt() and BYTE_MASK
            if (a != b) return a - b
        }
        return r.size - p.size
    }

    // ビット操作: シフト幅・マスク自体が意味を持つため MagicNumber を豁免
    @Suppress("MagicNumber")
    private fun be32(
        b: ByteArray,
        i: Int,
    ) = ((b[i].toInt() and BYTE_MASK) shl 24) or ((b[i + 1].toInt() and BYTE_MASK) shl 16) or
        ((b[i + 2].toInt() and BYTE_MASK) shl 8) or (b[i + 3].toInt() and BYTE_MASK)

    @Suppress("MagicNumber")
    private fun be16(
        b: ByteArray,
        i: Int,
    ) = ((b[i].toInt() and BYTE_MASK) shl 8) or (b[i + 1].toInt() and BYTE_MASK)

    companion object {
        // index entry: [u32 dataOffset][u16 readingLen][u8 wordLen][u16 cost] (big endian)
        private const val ENTRY_SIZE = 9
        private const val IDX_READING_LEN = 4
        private const val IDX_WORD_LEN = 6
        private const val IDX_COST = 7
        private const val HEADER_SIZE = 5 // [u32 count][u8 maxReadingLen]
        private const val MAX_READING_LEN_POS = 4
        private const val DATA_HEADER = 2 // data entry: [u8 readingLen][u8 wordLen]
        private const val BYTE_MASK = 0xFF

        @Volatile private var cache: MozcDict? = null

        fun get(context: Context): MozcDict =
            cache ?: synchronized(this) {
                cache ?: MozcDict(context.assets.open("mozc_dict.bin").use { it.readBytes() }).also { cache = it }
            }
    }
}
