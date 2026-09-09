package dev.example.jpkeyboard

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
// pi-lens-ignore: kotlin:UNRESOLVED_REFERENCE
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONException

/** 配列の選択 + カスタム配列(JSON)の編集画面。Material 3 Expressive テーマ。 */
class SettingsActivity : AppCompatActivity() {
    private companion object {
        const val TEXT_SIZE_SP = 18f
        const val MIN_LINES = 6
        const val PADDING_DP = 48
        const val LABEL_GAP_DP = 32
        const val LABEL_PAD_DP = 8
    }

    // pi-lens-ignore: kotlin:DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE, kotlin:UNRESOLVED_REFERENCE
    private val density by lazy { resources.displayMetrics.density }

    // pi-lens-ignore: kotlin:OVERLOAD_RESOLUTION_AMBIGUITY
    private fun Int.dp() = (this * density).toInt()

    // pi-lens-ignore: kotlin:NOTHING_TO_OVERRIDE, kotlin:UNRESOLVED_REFERENCE
    override fun onCreate(savedInstanceState: Bundle?) {
        // pi-lens-ignore: kotlin:UNRESOLVED_REFERENCE
        super.onCreate(savedInstanceState)
        // pi-lens-ignore: kotlin:UNRESOLVED_REFERENCE
        setContentView(buildContentView())
    }

    private fun buildContentView(): View {
        val group = layoutPicker()
        val edit = jsonEditor()
        val box =
            // pi-lens-ignore: kotlin:TYPE_MISMATCH
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(PADDING_DP.dp(), PADDING_DP.dp(), PADDING_DP.dp(), PADDING_DP.dp())
                addView(label("配列を選択", group.id))
                addView(group)
                addView(
                    label("カスタム配列 (JSON: {\"name\":…,\"rows\":[[\"a\",…],…]} )", edit.id).apply {
                        setPadding(0, LABEL_GAP_DP.dp(), 0, LABEL_PAD_DP.dp())
                    },
                )
                addView(edit)
                addView(saveButton(edit))
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

    private fun layoutPicker(): RadioGroup {
        val layouts = Layouts.load(this)
        // pi-lens-ignore: kotlin:TYPE_MISMATCH
        val group = RadioGroup(this).apply { id = View.generateViewId() }
        layouts.forEachIndexed { i, l ->
            group.addView(
                // pi-lens-ignore: kotlin:TYPE_MISMATCH
                RadioButton(this).apply {
                    text = l.name
                    id = i + 1
                    isChecked = i == Layouts.currentIndex(this@SettingsActivity)
                },
            )
        }
        group.setOnCheckedChangeListener { _, id ->
            // pi-lens-ignore: kotlin:TYPE_MISMATCH
            Layouts.setCurrentIndex(this, id - 1)
            // pi-lens-ignore: kotlin:NONE_APPLICABLE
            Toast.makeText(this, "選択: ${layouts[id - 1].name}", Toast.LENGTH_SHORT).show()
        }
        return group
    }

    private fun jsonEditor(): EditText =
        // pi-lens-ignore: kotlin:TYPE_MISMATCH
        EditText(this).apply {
            id = View.generateViewId()
            setMinLines(MIN_LINES)
            gravity = Gravity.TOP
            setText(Layouts.customJson(this@SettingsActivity) ?: Layouts.EXAMPLE_JSON)
        }

    private fun saveButton(edit: EditText): Button =
        // pi-lens-ignore: kotlin:TYPE_MISMATCH
        Button(this).apply {
            text = "カスタム配列を保存"
            setOnClickListener {
                try {
                    edit.error = null
                    // pi-lens-ignore: kotlin:TYPE_MISMATCH
                    Layouts.saveCustom(this@SettingsActivity, edit.text.toString())
                    // pi-lens-ignore: kotlin:NONE_APPLICABLE
                    Toast.makeText(this@SettingsActivity, "保存しました", Toast.LENGTH_SHORT).show()
                } catch (e: JSONException) {
                    // インラインエラー（Toast は消えて分からなくなるため）
                    edit.error = "JSONエラー: ${e.message}"
                    edit.requestFocus()
                }
            }
        }
}
