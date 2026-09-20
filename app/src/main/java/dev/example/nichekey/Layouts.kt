package dev.example.nichekey

import android.content.Context

data class Key(
    val label: String,
    val out: String = label,
    /** ローマ字変換に渡さず直接コミットする（数字行など） */
    val direct: Boolean = false,
)

data class Row(
    val keys: List<Key>,
)

data class Layout(
    val name: String,
    val rows: List<Row>,
)

object Layouts {
    private fun row(vararg keys: String) = Row(keys.map { Key(it) })

    /** 数字行: 全モードの最上段。直接コミットされ、ローマ字変換されない */
    private val NUMBER_ROW =
        Row(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { Key(it, direct = true) })

    // 各配列は一次情報から採取:
    //  Eucalyn   : https://eucalyn.hatenadiary.jp/entry/about-eucalyn-layout (決定版の配列図)
    //  Eucalyn改 : https://scrapbox.io/self-made-kbds-ja/Eucalyn改配列 (biacco42)
    //  大西      : https://o24.works/layout/ 公式 karabiner.json
    //  Tomisuke  : https://tomisuke.com/tomisuke-keyboard-layout/ 公式 JIS 配列図 / AHK
    val BUILTIN =
        listOf(
            Layout(
                "Eucalyn",
                listOf(
                    row("q", "w", ",", ".", ";", "m", "r", "d", "y", "p"),
                    row("a", "o", "e", "i", "u", "g", "t", "k", "s", "n"),
                    row("z", "x", "c", "v", "f", "b", "h", "j", "l", "/"),
                ),
            ),
            Layout(
                "Eucalyn改",
                listOf(
                    row(";", ",", ".", "p", "q", "y", "g", "d", "m", "f"),
                    row("a", "o", "e", "i", "u", "b", "n", "t", "r", "s"),
                    row("z", "x", "c", "v", "w", "h", "j", "k", "l", "/"),
                ),
            ),
            Layout(
                "大西",
                listOf(
                    row("q", "l", "u", ",", ".", "f", "w", "r", "y", "p"),
                    row("e", "i", "a", "o", "-", "k", "t", "n", "s", "h"),
                    row("z", "x", "c", "v", ";", "g", "d", "m", "j", "b"),
                ),
            ),
            Layout(
                "Tomisuke",
                listOf(
                    row(",", ".", "-", ";", "l", "r", "d", "y", "p"),
                    row("a", "o", "e", "i", "u", "g", "n", "t", "s", "k", "f"),
                    row("x", "c", "v", "w", "q", "j", "h", "m", "b", "z"),
                ),
            ),
            Layout(
                "Dvorak(JP)",
                listOf(
                    row("'", ",", ".", "p", "y", "f", "g", "c", "r", "l"),
                    row("a", "o", "e", "u", "i", "d", "h", "t", "n", "s"),
                    row(";", "q", "j", "k", "x", "b", "m", "w", "v", "z"),
                ),
            ),
        )

    /** 記号・数字ページ（直接コミットされ、ローマ字変換されない） */
    val SYMBOL =
        Layout(
            "記号",
            listOf(
                row("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                row("@", "#", "$", "%", "&", "*", "+", "=", "-", "_"),
                row("(", ")", "[", "]", "{", "}", "<", ">", "'", "\""),
                row(":", ";", "!", "?", "/", "\\", "^", "|", "¥", "ー"),
            ),
        )

    /** 最上段に数字行を足した編集レイアウト（かな入力・英字入力共通） */
    fun withNumberRow(l: Layout) = Layout(l.name, listOf(NUMBER_ROW) + l.rows)

    private fun prefs(c: Context) = c.getSharedPreferences("layouts", Context.MODE_PRIVATE)

    fun currentIndex(c: Context): Int = prefs(c).getInt("index", 0).coerceIn(0, BUILTIN.size - 1)

    fun setCurrentIndex(
        c: Context,
        i: Int,
    ) {
        prefs(c).edit().putInt("index", i.coerceIn(0, BUILTIN.size - 1)).apply()
    }

    fun current(c: Context): Layout = BUILTIN[currentIndex(c)]
}

/** キーボードのカラーテーマ（M3 トーナルパレット準拠） */
data class KeyboardTheme(
    val name: String,
    val bg: Int, // surface
    val key: Int, // surfaceContainerHigh
    val func: Int, // surfaceContainer
    val active: Int, // primaryContainer
    val text: Int, // onSurface
)

object Themes {
    val PRESETS =
        listOf(
            KeyboardTheme(
                "ダーク",
                bg = 0xFF141218.toInt(),
                key = 0xFF36343B.toInt(),
                func = 0xFF211F26.toInt(),
                active = 0xFF4A4458.toInt(),
                text = 0xFFE6E0E9.toInt(),
            ),
            KeyboardTheme(
                "ライト",
                bg = 0xFFFEF7FF.toInt(),
                key = 0xFFF3EDF7.toInt(),
                func = 0xFFECE6F0.toInt(),
                active = 0xFFD0BCFF.toInt(),
                text = 0xFF1D1B20.toInt(),
            ),
            KeyboardTheme(
                "ブルー",
                bg = 0xFF101418.toInt(),
                key = 0xFF2C3849.toInt(),
                func = 0xFF1B222B.toInt(),
                active = 0xFF3E4C63.toInt(),
                text = 0xFFDFE2EB.toInt(),
            ),
            KeyboardTheme(
                "ピンク",
                bg = 0xFF201A1B.toInt(),
                key = 0xFF3B2C2E.toInt(),
                func = 0xFF2A2021.toInt(),
                active = 0xFF633B48.toInt(),
                text = 0xFFEAE0E1.toInt(),
            ),
        )

    private fun prefs(c: Context) = c.getSharedPreferences("theme", Context.MODE_PRIVATE)

    fun currentIndex(c: Context): Int = prefs(c).getInt("index", 0).coerceIn(0, PRESETS.size - 1)

    fun setCurrentIndex(
        c: Context,
        i: Int,
    ) {
        prefs(c).edit().putInt("index", i.coerceIn(0, PRESETS.size - 1)).apply()
    }

    fun current(c: Context): KeyboardTheme = PRESETS[currentIndex(c)]
}
