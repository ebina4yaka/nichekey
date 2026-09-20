package dev.example.nichekey

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
// pi-lens-ignore: kotlin:UNRESOLVED_REFERENCE
import androidx.appcompat.app.AppCompatActivity
// pi-lens-ignore: kotlin:UNRESOLVED_REFERENCE
import androidx.core.view.ViewCompat
// pi-lens-ignore: kotlin:UNRESOLVED_REFERENCE
import androidx.core.view.WindowInsetsCompat

/** 配列の選択 + カラーテーマ選択画面。Material 3 Expressive テーマ。 */
class SettingsActivity : AppCompatActivity() {
    private companion object {
        const val TEXT_SIZE_SP = 18f
        const val PADDING_DP = 48
        const val LABEL_GAP_DP = 32
        const val LABEL_PAD_DP = 8
        const val SWATCH_DP = 44
        const val SWATCH_MARGIN_DP = 8
        const val SWATCH_STROKE_SELECTED_DP = 4
        const val SWATCH_STROKE_DP = 1
    }

    // pi-lens-ignore: kotlin:DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE, kotlin:UNRESOLVED_REFERENCE
    private val density by lazy { resources.displayMetrics.density }

    // pi-lens-ignore: kotlin:OVERLOAD_RESOLUTION_AMBIGUITY
    private fun Int.dp() = (this * density).toInt()

    /** レンダリング時点の IME 有効状態（設定から戻った時に変化していれば再構築） */
    private var renderedImeEnabled = false

    private fun isImeEnabled(): Boolean {
        val imm = getSystemService(InputMethodManager::class.java)
        return imm?.enabledInputMethodList.orEmpty().any { it.packageName == packageName }
    }

    // pi-lens-ignore: kotlin:NOTHING_TO_OVERRIDE, kotlin:UNRESOLVED_REFERENCE
    override fun onCreate(savedInstanceState: Bundle?) {
        // pi-lens-ignore: kotlin:UNRESOLVED_REFERENCE
        super.onCreate(savedInstanceState)
        renderedImeEnabled = isImeEnabled()
        val root = buildContentView()
        // edge-to-edge (targetSdk 35) でシステムバーの下に描画されないよう inset を適用
        // pi-lens-ignore: kotlin:TYPE_MISMATCH
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        // pi-lens-ignore: kotlin:UNRESOLVED_REFERENCE
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        // システム設定で IME を有効化して戻ってきたら画面を再構築
        if (renderedImeEnabled != isImeEnabled()) recreate()
        renderedImeEnabled = isImeEnabled()
    }

    private fun buildContentView(): View {
        val group = layoutPicker()
        val themeRow = themePicker()
        val box =
            // pi-lens-ignore: kotlin:TYPE_MISMATCH
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(PADDING_DP.dp(), PADDING_DP.dp(), PADDING_DP.dp(), PADDING_DP.dp())
                imeGuide()?.let { addView(it) }
                addView(label("配列を選択", group.id))
                addView(group)
                addView(label("テーマ", themeRow.id).apply {
                    setPadding(0, LABEL_GAP_DP.dp(), 0, LABEL_PAD_DP.dp())
                })
                addView(themeRow)
            }
        return ScrollView(this).apply { addView(box) }
    }

    private fun label(text: String, forId: Int): TextView =
        // pi-lens-ignore: kotlin:TYPE_MISMATCH
        TextView(this).apply {
            this.text = text
            textSize = TEXT_SIZE_SP
            labelFor = forId
        }

    /** IME が無効ならシステム設定への誘導を表示 */
    private fun imeGuide(): View? {
        if (isImeEnabled()) return null
        val box =
            // pi-lens-ignore: kotlin:TYPE_MISMATCH
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("⚠ システム設定で NicheKey キーボードが有効になっていません", View.NO_ID))
                addView(
                    // pi-lens-ignore: kotlin:TYPE_MISMATCH
                    Button(this@SettingsActivity).apply {
                        text = "キーボードを有効にする"
                        setOnClickListener {
                            // pi-lens-ignore: kotlin:TYPE_MISMATCH
                            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        }
                    },
                )
            }
        return box
    }

    private fun layoutPicker(): RadioGroup {
        val layouts = Layouts.BUILTIN
        val group = RadioGroup(this).apply { id = View.generateViewId() }
        layouts.forEachIndexed { i, l ->
            group.addView(
                // pi-lens-ignore: kotlin:TYPE_MISMATCH
                RadioButton(this).apply {
                    text = l.name
                    id = View.generateViewId() // i+1 だと generateViewId と衝突し選択解除が効かない
                    isChecked = i == Layouts.currentIndex(this@SettingsActivity)
                },
            )
        }
        group.setOnCheckedChangeListener { _, id ->
            val index = group.indexOfChild(group.findViewById(id))
            // pi-lens-ignore: kotlin:TYPE_MISMATCH
            Layouts.setCurrentIndex(this, index)
            // pi-lens-ignore: kotlin:NONE_APPLICABLE
            Toast.makeText(this, "選択: ${layouts[index].name}", Toast.LENGTH_SHORT).show()
        }
        return group
    }

    /** Gboard 風のカラースウォッチ（選択中はリング表示） */
    private fun themePicker(): View {
        val row =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                id = View.generateViewId()
            }
        val swatches = mutableListOf<GradientDrawable>()

        fun applySelection() {
            Themes.PRESETS.forEachIndexed { i, t ->
                val selected = i == Themes.currentIndex(this@SettingsActivity)
                swatches[i].setColor(t.active)
                swatches[i].setStroke((if (selected) SWATCH_STROKE_SELECTED_DP else SWATCH_STROKE_DP).dp(), t.text)
            }
        }

        Themes.PRESETS.forEachIndexed { i, t ->
            val gd = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            swatches.add(gd)
            row.addView(
                View(this).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(SWATCH_DP.dp(), SWATCH_DP.dp()).apply {
                            setMargins(
                                SWATCH_MARGIN_DP.dp(), SWATCH_MARGIN_DP.dp(),
                                SWATCH_MARGIN_DP.dp(), SWATCH_MARGIN_DP.dp(),
                            )
                        }
                    background = gd
                    contentDescription = t.name
                    setOnClickListener {
                        // pi-lens-ignore: kotlin:TYPE_MISMATCH
                        Themes.setCurrentIndex(this@SettingsActivity, i)
                        applySelection()
                    }
                },
            )
        }
        applySelection()
        return row
    }
}
