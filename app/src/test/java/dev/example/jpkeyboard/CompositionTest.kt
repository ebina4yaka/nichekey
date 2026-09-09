package dev.example.jpkeyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class CompositionTest {
    private fun type(vararg keys: String) = keys.fold(Composition.State()) { s, k -> Composition.type(s, k) }

    @Test fun basic() {
        assertEquals("か", Composition.display(type("k", "a")))
        assertEquals("しんぶん", Composition.display(type("s", "h", "i", "n", "b", "u", "n", "n")))
    }

    @Test fun pendingN() {
        assertEquals("こn", Composition.display(type("k", "o", "n")))
        assertEquals("こん", Composition.display(type("k", "o", "n", "n")))
        assertEquals("こんな", Composition.display(type("k", "o", "n", "n", "a")))
    }

    @Test fun pendingNReading() {
        assertEquals("しん", Composition.reading(type("s", "h", "i", "n")))
        assertEquals("かんじ", Composition.reading(type("k", "a", "n", "j", "i")))
    }

    @Test fun backspaceDeletesOneKanaChar() {
        // Google 日本語入力と同じ:「きゃ」→ Backspace →「き」
        val s = type("k", "y", "a")
        assertEquals("きゃ", Composition.display(s))
        val b1 = Composition.backspace(s)
        assertEquals("き", Composition.display(b1))
        assertEquals("", Composition.display(Composition.backspace(b1)))
    }

    @Test fun backspaceTailFirst() {
        // 未確定ローマ字は1文字ずつ
        assertEquals("か", Composition.display(Composition.backspace(type("k", "a", "n"))))
        assertEquals("こn", Composition.display(Composition.backspace(type("k", "o", "n", "n"))))
    }

    @Test fun sokuon() {
        val s = type("t", "t", "a")
        assertEquals("った", Composition.display(s))
        assertEquals("っ", Composition.display(Composition.backspace(s)))
        assertEquals("", Composition.display(Composition.backspace(Composition.backspace(s))))
    }

    @Test fun nApostrophe() {
        assertEquals("ん", Composition.display(type("n", "'")))
    }

    @Test fun prolongedSoundMark() {
        // ローマ字モードの "-" キーで長音「ー」を入力
        assertEquals("ふぁいるー", Composition.display(type("f", "a", "i", "r", "u", "-")))
        // 保留中の n は ん に確定してから ー
        assertEquals("こんー", Composition.display(type("k", "o", "n", "-")))
        assertEquals("こんー", Composition.reading(type("k", "o", "n", "-")))
    }
}
