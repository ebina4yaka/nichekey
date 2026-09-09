package dev.example.jpkeyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.KeyEvent
import android.view.View
import kotlin.concurrent.thread

/**
 * ローマ字入力 IME。
 * 構成状態は Composition（確定かな + 未確定ローマ字）。変換は WanaKana。
 * 空白で Mozc 辞書によるかな→漢字変換候補を巡回（カタカナ・ひらがな含む）、確定でコミット。
 * シフト中・記号ページのキーは直接コミットされる。
 */
class JpImeService : InputMethodService() {
    private var state = Composition.State()
    private var candidates: List<String> = emptyList()
    private var candIndex = 0
    private var view: KeyboardView? = null

    private fun show(s: String) {
        Log.d("JpIme", "show: kana=\"${state.kana}\" tail=\"${state.tail}\" → \"$s\"")
        currentInputConnection?.setComposingText(s, 1)
    }

    private fun reset() {
        state = Composition.State()
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
                state = Composition.type(state, text)
                candidates = emptyList()
                candIndex = 0
                show(Composition.display(state))
            } else {
                currentInputConnection?.commitText(text, 1)
            }
        }
        v.onBackspace = {
            if (candidates.isNotEmpty()) {
                // 候補表示中の Backspace は変換を取り消して読みに戻す（Google 日本語入力と同じ）
                candidates = emptyList()
                candIndex = 0
                show(Composition.display(state))
            } else {
                val next = Composition.backspace(state)
                if (next != state) {
                    state = next
                    candidates = emptyList()
                    candIndex = 0
                    show(Composition.display(state))
                } else {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                }
            }
        }
        v.onEnter = {
            if (state != Composition.State()) {
                // ローマ字入力（または変換候補表示）中は確定
                currentInputConnection?.finishComposingText()
                reset()
            } else {
                // 未入力時は Enter キーとして動作（改行・送信などアプリ側の挙動に委ねる）
                val ic = currentInputConnection
                ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
        }
        v.onSpace = {
            val ic = currentInputConnection
            if (state == Composition.State()) {
                ic?.commitText(" ", 1)
            } else {
                if (candidates.isEmpty()) {
                    candidates = Candidates.forReading(applicationContext, Composition.reading(state))
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
        // 設定画面で変更した配列・テーマを表示のたびに反映する
        view?.layout = Layouts.current(this)
        view?.theme = Themes.current(this)
    }

    override fun onStartInput(
        attribute: android.view.inputmethod.EditorInfo?,
        restarting: Boolean,
    ) {
        super.onStartInput(attribute, restarting)
        reset()
    }
}
