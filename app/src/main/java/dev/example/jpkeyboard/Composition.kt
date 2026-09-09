package dev.example.jpkeyboard

import dev.esnault.wanakana.core.IMEMode
import dev.esnault.wanakana.core.Wanakana

/**
 * ローマ字→かなの構成状態（PC IME 相当）。
 * kana = 確定済みかな、tail = 未確定ローマ字。変換は WanaKana に委譲。
 * - 表示: kana + toKana(tail, IME)（未確定部分はローマ字のまま表示）
 * - Backspace: tail を1文字、なければかな1文字削る（「きゃ」→「き」）
 * - 読み（辞書変換用）: 未確定 n を ん に解決
 */
object Composition {
    data class State(
        val kana: String = "",
        val tail: String = "",
    )

    // 次の1文字で確定結果が変わるため確定を保留する tail
    private val PENDING_N = setOf("n", "nn")

    private const val ASCII_MAX = 0x80

    fun type(
        s: State,
        ch: String,
    ): State =
        when {
            // 長音記号: 保留中の n は ん に確定してから ー を追加
            ch == "-" -> State(s.kana + (if (s.tail == "n" || s.tail == "nn") "ん" else "") + "ー", "")
            s.tail == "nn" -> {
                // 2個目の n は次の入力と結合しうる（"nna"→んな）。1個目だけ ん として確定して残す。
                type(State(s.kana + "ん", ""), "n" + ch)
            }
            else -> typeTail(s, s.tail + ch)
        }

    private fun typeTail(
        s: State,
        tail: String,
    ): State {
        val conv = Wanakana.toKana(tail, IMEMode.ENABLED)
        return when {
            tail in PENDING_N -> State(s.kana, tail)
            conv.isNotEmpty() && conv.none { it.code < ASCII_MAX } -> State(s.kana + conv, "")
            else -> State(s.kana, tail)
        }
    }

    fun backspace(s: State): State =
        when {
            s.tail.isNotEmpty() -> State(s.kana, s.tail.dropLast(1))
            s.kana.isNotEmpty() -> State(s.kana.dropLast(1), "")
            else -> s
        }

    /** 画面表示（composing text） */
    fun display(s: State): String = s.kana + Wanakana.toKana(s.tail, IMEMode.ENABLED)

    /** 辞書変換に渡す読み（未確定 n を ん に解決） */
    fun reading(s: State): String =
        s.kana +
            when (s.tail) {
                "" -> ""
                "n", "nn" -> "ん"
                else -> Wanakana.toKana(s.tail)
            }
}
