package dev.example.jpkeyboard

import android.content.Context

/** 変換候補生成: Mozc辞書のN-best + カタカナ + ひらがな。 */
object Candidates {
    fun forReading(
        context: Context,
        kana: String,
    ): List<String> {
        if (kana.isEmpty()) return emptyList()
        val out = Converter.nbest(kana, MozcDict.get(context), 8).toMutableList()
        val kata = Converter.hira2kata(kana)
        out.remove(kata)
        out.add(kata)
        out.remove(kana)
        out.add(kana)
        return out
    }
}
