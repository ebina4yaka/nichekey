package dev.example.nichekey

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

    @Test fun mergeKeepsTopCandidate() {
        // 回帰: 1位の変換結果がカタカナ補完で末尾に移動しないこと
        val out = Candidates.merge("ふぁいる", listOf("ファイル", "ファ居る"))
        assertEquals("ファイル", out[0])
        assertEquals("ファ居る", out[1])
        assertEquals("ふぁいる", out.last()) // ひらがなは末尾に補完
    }

    @Test fun mergeAppendsKanaVariants() {
        val out = Candidates.merge("わたしは", listOf("私は"))
        assertEquals(listOf("私は", "ワタシハ", "わたしは"), out)
    }

    @Test fun connectionCostOrdersPaths() {
        // 単語コストが同点でも、品詞接続コストで順位が変わる（bigram 動作確認）
        val source =
            object : Converter.Source {
                override fun lookup(r: String): List<Converter.Word> =
                    when (r) {
                        "ねこ" ->
                            listOf(
                                Converter.Word("猫", 1000, leftId = 1, rightId = 1),
                                Converter.Word("根古", 1000, leftId = 2, rightId = 2),
                            )
                        "が" -> listOf(Converter.Word("が", 1000, leftId = 1, rightId = 1))
                        else -> emptyList()
                    }

                override fun connection(
                    rightId: Int,
                    leftId: Int,
                ): Int = if (rightId == 2 && leftId == 1) 0 else 9000
            }
        val out = Converter.nbest("ねこが", source, 2)
        assertEquals("根古が", out[0]) // 接続コスト 0 の経路が優先
        assertEquals("猫が", out[1])
    }
}
