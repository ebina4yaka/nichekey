package dev.example.jpkeyboard

/**
 * ponytail: かな→漢字は「全文一致の小型辞書 + かな送り」のみ。
 * 実用変換（文節単位・学習・予測）は Mozc 等の辞書エンジンへの差し替え前提。
 */
object KanaKanji {

    private val DICT = mapOf(
        "わたし" to listOf("私"), "きょう" to listOf("今日"), "あした" to listOf("明日"),
        "ありがとう" to listOf("有難う"), "こんにちは" to listOf("今日は"), "おはよう" to listOf("お早う"),
        "いま" to listOf("今"), "ひと" to listOf("人"), "とき" to listOf("時"),
        "みず" to listOf("水"), "ねこ" to listOf("猫"), "いぬ" to listOf("犬"),
        "にほん" to listOf("日本"), "にほんご" to listOf("日本語"),
        "かんじ" to listOf("漢字"), "ひらがな" to listOf("平仮名"), "かたかな" to listOf("片仮名"),
        "でんわ" to listOf("電話"), "がっこう" to listOf("学校"), "しごと" to listOf("仕事"),
        "たべる" to listOf("食べる"), "のむ" to listOf("飲む"), "いく" to listOf("行く"),
        "くる" to listOf("来る"), "たかい" to listOf("高い"), "おおきい" to listOf("大きい"),
        "ちいさい" to listOf("小さい"), "あたらしい" to listOf("新しい"), "おかね" to listOf("お金"),
        "げんき" to listOf("元気"), "だいじょうぶ" to listOf("大丈夫"), "すみません" to listOf("済みません"),
    )

    fun candidates(kana: String): List<String> = (DICT[kana] ?: emptyList()) + kana
}
