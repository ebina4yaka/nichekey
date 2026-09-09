package dev.example.jpkeyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import kotlin.concurrent.thread

/**
 * ローマ字入力 IME。
 * バッファはローマ字文字列のみ。表示は常に Romaji.convert(raw)。
 * 空白で Mozc 辞書によるかな→漢字変換候補を巡回（カタカナ・ひらがな含む）、確定でコミット。
 * シフト中・記号ページのキーは直接コミットされる。
 */
class JpImeService : InputMethodService() {
    private var raw = ""
    private var candidates: List<String> = emptyList()
    private var candIndex = 0
    private var view: KeyboardView? = null

    private val kana: String get() = Romaji.convert(raw)

    private fun show(s: String) {
        Log.d("JpIme", "show: raw=\"$raw\" → \"$s\"")
        currentInputConnection?.setComposingText(s, 1)
    }

    private fun reset() {
        raw = ""
        candidates = emptyList()
        candIndex = 0
    }

    override fun onCreateInputView(): View {
        // 44MB 辞書の初回読込を裏で温めておく
        thread(name = "mozc-dict-warmup") { runCatching { MozcDict.get(applicationContext) } }
        val v = KeyboardView(this)
        v.layout = Layouts.current(this)
        v.onKey = { text, composing ->
            if (composing) {
                raw += text
                candidates = emptyList()
                candIndex = 0
                show(kana)
            } else {
                currentInputConnection?.commitText(text, 1)
            }
        }
        v.onBackspace = {
            if (raw.isNotEmpty()) {
                // かな1単位（例: "ka"→"か"）にまとめて削除する
                raw = Romaji.deleteLastUnit(raw)
                candidates = emptyList()
                candIndex = 0
                show(kana)
            } else {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
        }
        v.onEnter = {
            currentInputConnection?.finishComposingText()
            reset()
        }
        v.onSpace = {
            val ic = currentInputConnection
            if (raw.isEmpty()) {
                ic?.commitText(" ", 1)
            } else {
                if (candidates.isEmpty()) {
                    candidates = Candidates.forReading(applicationContext, kana)
                } else {
                    candIndex = (candIndex + 1) % candidates.size
                }
                Log.d("JpIme", "candidates: $candidates (index=$candIndex)")
                ic?.setComposingText(candidates[candIndex], 1)
            }
        }
        view = v
        return v
    }

    override fun onStartInputView(
        attribute: android.view.inputmethod.EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInputView(attribute, restarting)
        // 設定画面で変更した配列を表示のたびに反映する
        view?.layout = Layouts.current(this)
    }

    override fun onStartInput(
        attribute: android.view.inputmethod.EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInput(attribute, restarting)
        reset()
    }
}
