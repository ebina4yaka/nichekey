package dev.example.jpkeyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Key(
    val label: String,
    val out: String = label,
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

    // 各配列は一次情報から採取:
    //  Eucalyn   : https://eucalyn.hatenadiary.jp/entry/about-eucalyn-layout (決定版の配列図)
    //  Eucalyn改 : https://scrapbox.io/self-made-kbds-ja/Eucalyn改配列 (biacco42)
    //  大西      : https://o24.works/layout/ 公式 karabiner.json
    //  Tomisuke  : https://tomisuke.com/tomisuke-keyboard-layout/ 公式 JIS 配列図 / AHK
    val BUILTIN =
        listOf(
            Layout(
                "QWERTY",
                listOf(
                    row("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                    row("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                    row("z", "x", "c", "v", "b", "n", "m", ",", "."),
                ),
            ),
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
                row(":", ";", "!", "?", "/", "\\", "~", "^", "|", "¥"),
            ),
        )

    const val EXAMPLE_JSON =
        """{"name":"独自","rows":[["q","w","e","r","t","y","u","i","o","p"],["a","s","d","f","g","h","j","k","l"],["z","x","c","v","b","n","m",",","."]]}"""

    fun parse(json: String): Layout {
        val rows = JSONArray(json) // {"name":"…","rows":[["a",…],[…],[…]]} or {"rows":[…]}
        val obj = runCatching { JSONObject(json) }.getOrNull()
        val name = obj?.optString("name", "独自") ?: "独自"
        val rowArr = obj?.getJSONArray("rows") ?: rows
        return Layout(
            name,
            (0 until rowArr.length()).map { r ->
                val ka = rowArr.getJSONArray(r)
                Row((0 until ka.length()).map { Key(ka.getString(it)) })
            },
        )
    }

    private fun prefs(c: Context) = c.getSharedPreferences("layouts", Context.MODE_PRIVATE)

    fun customJson(c: Context): String? = prefs(c).getString("custom", null)

    fun saveCustom(
        c: Context,
        json: String,
    ) {
        parse(json) // 検証してから保存
        prefs(c).edit().putString("custom", json).apply()
    }

    fun load(c: Context): List<Layout> {
        val custom = customJson(c)?.let { runCatching { parse(it) }.getOrNull() }
        return BUILTIN + listOfNotNull(custom)
    }

    fun currentIndex(c: Context): Int = prefs(c).getInt("index", 0).coerceIn(0, load(c).size - 1)

    fun setCurrentIndex(
        c: Context,
        i: Int,
    ) {
        prefs(c).edit().putInt("index", i.coerceIn(0, load(c).size - 1)).apply()
    }

    fun current(c: Context): Layout = load(c)[currentIndex(c)]
}
