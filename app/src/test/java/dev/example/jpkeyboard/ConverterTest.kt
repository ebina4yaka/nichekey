package dev.example.jpkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConverterTest {
    private val fake =
        Converter.Source { r ->
            when (r) {
                "わたし" -> listOf(Converter.Word("私", 3000), Converter.Word("渡し", 6000))
                "は" -> listOf(Converter.Word("は", 2000), Converter.Word("歯", 4000))
                "げんき" -> listOf(Converter.Word("元気", 3000))
                "です" -> listOf(Converter.Word("です", 2000))
                else -> emptyList()
            }
        }

    @Test fun bestPath() {
        val out = Converter.nbest("わたしは", fake, 5)
        assertEquals("私は", out[0]) // 単漢字「歯」より低コストの「は」+「私」
    }

    @Test fun nbestDistinct() {
        val out = Converter.nbest("わたし", fake, 5)
        assertTrue(out.contains("私"))
        assertTrue(out.contains("渡し"))
        assertTrue(out.contains("わたし")) // フォールバック1文字ノードの経路
        assertEquals(out.size, HashSet(out).size) // 重複なし
    }

    @Test fun connectivityWithGaps() {
        // 辞書にない文字はフォールバックノードでつながる
        val out = Converter.nbest("げんきです", fake, 3)
        assertEquals("元気です", out[0])
        assertTrue(out.size >= 2) // げんきです（フォールバック経路）も候補に入る
    }

    @Test fun hira2kata() {
        assertEquals("ゲンキ", Converter.hira2kata("げんき"))
        assertEquals("コンニチハ", Converter.hira2kata("こんにちは"))
        assertEquals("ー", Converter.hira2kata("ー"))
    }
}
