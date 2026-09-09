package dev.example.jpkeyboard

import android.app.Activity
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
import org.json.JSONException

/** 配列の選択 + カスタム配列(JSON)の編集画面。 */
class SettingsActivity : Activity() {
    private companion object {
        const val TEXT_SIZE_SP = 18f
        const val MIN_LINES = 6
        const val PADDING_DP = 48
        const val LABEL_GAP_DP = 32
        const val LABEL_PAD_DP = 8
    }

    private val density by lazy { resources.displayMetrics.density }

    private fun Int.dp() = (this * density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
    }

    private fun buildContentView(): View {
        val group = layoutPicker()
        val edit = jsonEditor()
        val box =
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
        TextView(this).apply {
            this.text = text
            textSize = TEXT_SIZE_SP
            labelFor = forId
        }

    private fun layoutPicker(): RadioGroup {
        val layouts = Layouts.load(this)
        val group = RadioGroup(this).apply { id = View.generateViewId() }
        layouts.forEachIndexed { i, l ->
            group.addView(
                RadioButton(this).apply {
                    text = l.name
                    id = i + 1
                    isChecked = i == Layouts.currentIndex(this@SettingsActivity)
                },
            )
        }
        group.setOnCheckedChangeListener { _, id ->
            Layouts.setCurrentIndex(this, id - 1)
            Toast.makeText(this, "選択: ${layouts[id - 1].name}", Toast.LENGTH_SHORT).show()
        }
        return group
    }

    private fun jsonEditor(): EditText =
        EditText(this).apply {
            id = View.generateViewId()
            setMinLines(MIN_LINES)
            gravity = Gravity.TOP
            setText(Layouts.customJson(this@SettingsActivity) ?: Layouts.EXAMPLE_JSON)
        }

    private fun saveButton(edit: EditText): Button =
        Button(this).apply {
            text = "カスタム配列を保存"
            setOnClickListener {
                try {
                    edit.error = null
                    Layouts.saveCustom(this@SettingsActivity, edit.text.toString())
                    Toast.makeText(this@SettingsActivity, "保存しました", Toast.LENGTH_SHORT).show()
                } catch (e: JSONException) {
                    // インラインエラー（Toast は消えて分からなくなるため）
                    edit.error = "JSONエラー: ${e.message}"
                    edit.requestFocus()
                }
            }
        }
}
