package dev.example.jpkeyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View

/**
 * ローマ字入力 IME。
 * バッファはローマ字文字列のみ。表示は常に Romaji.convert(raw)。
 * 空白でかな→漢字候補を巡回、確定でコミット。
 */
class JpImeService : InputMethodService() {

    private var raw = ""
    private var candidates: List<String> = emptyList()
    private var candIndex = 0

    private val kana: String get() = Romaji.convert(raw)

    private fun show(s: String) {
        Log.d("JpIme", "show: raw=\"$raw\" → \"$s\"")
        currentInputConnection?.setComposingText(s, 1)
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this)
        v.layout = Layouts.current(this)
        v.onKey = { c ->
            raw += c; candidates = emptyList(); candIndex = 0; show(kana)
        }
        v.onBackspace = {
            if (raw.isNotEmpty()) {
                raw = raw.dropLast(1); candidates = emptyList(); candIndex = 0; show(kana)
            } else {
                currentInputConnection?.deleteSurroundingText(1, 0)
            }
        }
        v.onEnter = {
            currentInputConnection?.finishComposingText()
            raw = ""; candidates = emptyList(); candIndex = 0
        }
        v.onSpace = {
            val ic = currentInputConnection
            if (raw.isEmpty()) {
                ic?.commitText(" ", 1)
            } else {
                if (candidates.isEmpty()) candidates = KanaKanji.candidates(kana)
                else candIndex = (candIndex + 1) % candidates.size
                Log.d("JpIme", "candidates: $candidates (index=$candIndex)")
                ic?.setComposingText(candidates[candIndex], 1)
            }
        }
        v.onSwitchLayout = {
            val list = Layouts.load(this)
            val next = (Layouts.currentIndex(this) + 1) % list.size
            Layouts.setCurrentIndex(this, next)
            v.layout = list[next]
        }
        return v
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        raw = ""; candidates = emptyList(); candIndex = 0
    }
}
