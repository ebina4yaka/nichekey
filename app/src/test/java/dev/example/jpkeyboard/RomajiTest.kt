package dev.example.jpkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class RomajiTest {
    @Test fun basic() = assertEquals("こんにちは", Romaji.convert("konnichiha"))

    @Test fun sokuon() = assertEquals("がんばって", Romaji.convert("ganbatte"))

    @Test fun youon() = assertEquals("にゃんこ", Romaji.convert("nyanko"))

    @Test fun pending() {
        assertEquals("n", Romaji.convert("n"))
        assertEquals("きn", Romaji.convert("kin"))
    }

    @Test fun punctuation() = assertEquals("はい。", Romaji.convert("hai."))

    @Test fun xSmall() = assertEquals("っぁ", Romaji.convert("xtuxa"))

    @Test fun deleteLastUnit() {
        // かな1単位（モーラ）で削る
        assertEquals("", Romaji.deleteLastUnit("ka")) // か を消す
        assertEquals("kaki", Romaji.deleteLastUnit("kakida")) // だ を消す
        assertEquals("ka", Romaji.deleteLastUnit("kann")) // ん を消す（"nn"=1単位）
        assertEquals("ki", Romaji.deleteLastUnit("kinn")) // ん を消す
        assertEquals("ka", Romaji.deleteLastUnit("kan")) // 未確定の末尾 n だけ消す
        assertEquals("", Romaji.deleteLastUnit("kya")) // きゃ を消す
    }
}
