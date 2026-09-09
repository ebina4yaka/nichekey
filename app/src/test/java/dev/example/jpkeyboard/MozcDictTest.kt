package dev.example.jpkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MozcDictTest {
    /** tools/build_mozc_dict.py と同じ形式の小さな辞書を作る */
    private fun dict(entries: List<Triple<String, String, Int>>): MozcDict {
        val sorted =
            entries.sortedWith(
                compareBy(
                    { it.first.toByteArray(Charsets.UTF_8).decodeToString() },
                    { it.second },
                ),
            )
        val data = ByteArrayOutputStream()
        val index = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        for ((reading, word, _) in sorted) {
            val rb = reading.toByteArray(Charsets.UTF_8)
            val wb = word.toByteArray(Charsets.UTF_8)
            offsets.add(data.size())
            data.write(byteArrayOf(rb.size.toByte()))
            data.write(rb)
            data.write(byteArrayOf(wb.size.toByte()))
            data.write(wb)
        }
        for (i in sorted.indices) {
            val rb = sorted[i].first.toByteArray(Charsets.UTF_8)
            val wb = sorted[i].second.toByteArray(Charsets.UTF_8)
            val cost = sorted[i].third

            fun be16(v: Int) = byteArrayOf(((v shr 8) and 0xFF).toByte(), (v and 0xFF).toByte())

            fun be32(v: Int) =
                byteArrayOf(
                    ((v shr 24) and 0xFF).toByte(),
                    ((v shr 16) and 0xFF).toByte(),
                    ((v shr 8) and 0xFF).toByte(),
                    (v and 0xFF).toByte(),
                )
            index.write(be32(offsets[i]))
            index.write(be16(rb.size))
            index.write(byteArrayOf(wb.size.toByte()))
            index.write(be16(cost))
        }
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 0, sorted.size.toByte())) // count (u32)
        out.write(sorted.maxOf { it.first.toByteArray(Charsets.UTF_8).size }) // maxReadingLen (u8)
        out.write(data.toByteArray())
        out.write(index.toByteArray())
        val ctor = MozcDict::class.java.getDeclaredConstructor(ByteArray::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(out.toByteArray()) as MozcDict
    }

    @Test fun exactMatch() {
        val d =
            dict(
                listOf(
                    Triple("きょう", "今日", 3000),
                    Triple("きょう", "協", 5000),
                    Triple("は", "は", 1000),
                ),
            )
        val words = d.lookup("きょう")
        assertEquals(listOf("今日", "協"), words.map { it.word })
        assertEquals(listOf(3000, 5000), words.map { it.cost })
    }

    @Test fun noPrefixMatchLeak() {
        val d =
            dict(
                listOf(
                    Triple("きょう", "今日", 3000),
                    Triple("きん", "金", 2000),
                ),
            )
        assertTrue(d.lookup("きょ").isEmpty()) // 前方一致では引っかからない
        assertTrue(d.lookup("きょうと").isEmpty())
        assertEquals("金", d.lookup("きん").single().word)
    }

    @Test fun utf8OrderLookup() {
        val d =
            dict(
                listOf(
                    Triple("あ", "亜", 1000),
                    Triple("い", "位", 1000),
                    Triple("ん", "無", 1000),
                ),
            )
        assertEquals("位", d.lookup("い").single().word)
        assertTrue(d.lookup("う").isEmpty())
    }
}
