package dev.example.nichekey

import android.content.Context

/** 変換候補生成: Mozc辞書のN-best + カタカナ + ひらがな。 */
object Candidates {
    private const val MAX_CANDIDATES = 8

    fun forReading(
        context: Context,
        kana: String,
    ): List<String> =
        if (kana.isEmpty()) {
            emptyList()
        } else {
            merge(kana, Converter.nbest(kana, MozcDict.get(context), MAX_CANDIDATES))
        }

    /** N-best にカタカナ・ひらがなを補完する（既に含まれる語は順位を変えない） */
    fun merge(
        kana: String,
        nbest: List<String>,
    ): List<String> {
        val out = nbest.toMutableList()
        val kata = Converter.hira2kata(kana)
        if (kata !in out) out.add(kata)
        if (kana !in out) out.add(kana)
        return out
    }
}
