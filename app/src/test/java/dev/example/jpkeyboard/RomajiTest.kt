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
}
