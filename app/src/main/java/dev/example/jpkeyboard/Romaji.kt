package dev.example.jpkeyboard

/** ローマ字 → ひらがな変換。全レイアウト共通（ローマ字入力前提）。 */
object Romaji {
    private val VOWELS = "aiueo"

    private val TABLE = HashMap<String, String>()

    init {
        val rows =
            listOf(
                "k" to arrayOf("か", "き", "く", "け", "こ"),
                "s" to arrayOf("さ", "し", "す", "せ", "そ"),
                "t" to arrayOf("た", "ち", "つ", "て", "と"),
                "n" to arrayOf("な", "に", "ぬ", "ね", "の"),
                "h" to arrayOf("は", "ひ", "ふ", "へ", "ほ"),
                "m" to arrayOf("ま", "み", "む", "め", "も"),
                "r" to arrayOf("ら", "り", "る", "れ", "ろ"),
                "g" to arrayOf("が", "ぎ", "ぐ", "げ", "ご"),
                "z" to arrayOf("ざ", "じ", "ず", "ぜ", "ぞ"),
                "d" to arrayOf("だ", "ぢ", "づ", "で", "ど"),
                "b" to arrayOf("ば", "び", "ぶ", "べ", "ぼ"),
                "p" to arrayOf("ぱ", "ぴ", "ぷ", "ぺ", "ぽ"),
                "v" to arrayOf("ゔぁ", "ゔぃ", "ゔ", "ゔぇ", "ゔぉ"),
                "w" to arrayOf("わ", "", "", "", "を"),
            )
        listOf("あ", "い", "う", "え", "お").forEachIndexed { i, k -> TABLE[VOWELS[i].toString()] = k }
        for ((c, ks) in rows) ks.forEachIndexed { i, k -> if (k.isNotEmpty()) TABLE[c + VOWELS[i]] = k }

        // 拗音
        for ((c, ks) in rows) {
            if (c == "w" || c == "v") continue
            TABLE[c + "ya"] = ks[1] + "ゃ"
            TABLE[c + "yu"] = ks[1] + "ゅ"
            TABLE[c + "yo"] = ks[1] + "ょ"
        }

        TABLE.putAll(
            mapOf(
                "shi" to "し",
                "sha" to "しゃ",
                "shu" to "しゅ",
                "she" to "しぇ",
                "sho" to "しょ",
                "chi" to "ち",
                "cha" to "ちゃ",
                "chu" to "ちゅ",
                "che" to "ちぇ",
                "cho" to "ちょ",
                "tsu" to "つ",
                "tsa" to "つぁ",
                "tse" to "つぇ",
                "tso" to "つぉ",
                "fu" to "ふ",
                "fa" to "ふぁ",
                "fi" to "ふぃ",
                "fe" to "ふぇ",
                "fo" to "ふぉ",
                "ji" to "じ",
                "ja" to "じゃ",
                "ju" to "じゅ",
                "je" to "じぇ",
                "jo" to "じょ",
                "wi" to "うぃ",
                "we" to "うぇ",
                "n'" to "ん",
                "xn" to "ん",
                "xtu" to "っ",
                "xtsu" to "っ",
                "ltu" to "っ",
                "ltsu" to "っ",
                "-" to "ー",
                "," to "、",
                "." to "。",
            ),
        )
        val small =
            mapOf(
                "xa" to "ぁ",
                "xi" to "ぃ",
                "xu" to "ぅ",
                "xe" to "ぇ",
                "xo" to "ぉ",
                "la" to "ぁ",
                "li" to "ぃ",
                "lu" to "ぅ",
                "le" to "ぇ",
                "lo" to "ぉ",
                "xya" to "ゃ",
                "xyu" to "ゅ",
                "xyo" to "ょ",
                "lya" to "ゃ",
                "lyu" to "ゅ",
                "lyo" to "ょ",
            )
        TABLE.putAll(small)
    }

    /** 各ステップの (消費ローマ字長, 出力) を返す。convert と deleteLastUnit の基盤。 */
    private fun scan(s: String): List<Pair<Int, String>> {
        val steps = mutableListOf<Pair<Int, String>>()
        var i = 0
        while (i < s.length) {
            var matched = false
            for (len in minOf(4, s.length - i) downTo 2) {
                val k = TABLE[s.substring(i, i + len)]
                if (k != null) {
                    steps.add(len to k)
                    i += len
                    matched = true
                    break
                }
            }
            if (matched) continue
            val c = s[i]
            val next = s.getOrNull(i + 1)
            when {
                c == 'n' && next == null -> {
                    steps.add(1 to "n") // 未確定
                    i += 1
                }

                c == 'n' && next == 'n' -> {
                    val after = s.getOrNull(i + 2)
                    val consume2 = after == null || after !in VOWELS
                    steps.add((if (consume2) 2 else 1) to "ん")
                    i += if (consume2) 2 else 1
                }

                c == 'n' && next != null && next !in VOWELS && next != 'y' -> {
                    steps.add(1 to "ん")
                    i += 1
                }

                c.isLetter() && c !in VOWELS && c != 'n' && next == c -> {
                    // "tt"→っ の1文字を消費（次の子音は次のループで母音と結合する）
                    steps.add(1 to "っ")
                    i += 1
                }

                TABLE[c.toString()] != null -> {
                    steps.add(1 to TABLE[c.toString()]!!)
                    i += 1
                }

                else -> {
                    steps.add(1 to c.toString())
                    i += 1
                }
            }
        }
        return steps
    }

    /** 貪欲最長一致。確定できない末尾（"n" 単独など）はローマ字のまま残す。 */
    fun convert(s: String): String = scan(s).joinToString("") { it.second }

    /** 末尾のローマ字を「かな1単位分」だけ削る（Backspace 用）。 */
    fun deleteLastUnit(raw: String): String {
        val steps = scan(raw)
        if (steps.isEmpty()) return raw
        return raw.dropLast(steps.last().first)
    }
}
