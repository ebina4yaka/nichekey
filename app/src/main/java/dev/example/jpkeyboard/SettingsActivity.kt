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

/** 配列の選択 + カスタム配列(JSON)の編集画面。 */
class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this
        val den = ctx.resources.displayMetrics.density

        fun dp(v: Int) = (v * den).toInt()
        val layouts = Layouts.load(ctx)

        val group = RadioGroup(ctx).apply { id = View.generateViewId() }
        layouts.forEachIndexed { i, l ->
            val rb =
                RadioButton(ctx).apply {
                    text = l.name
                    id = i + 1
                    isChecked = i == Layouts.currentIndex(ctx)
                }
            group.addView(rb)
        }
        group.setOnCheckedChangeListener { _, id ->
            Layouts.setCurrentIndex(ctx, id - 1)
            Toast.makeText(ctx, "選択: ${layouts[id - 1].name}", Toast.LENGTH_SHORT).show()
        }

        val edit =
            EditText(ctx).apply {
                id = View.generateViewId()
                setMinLines(6)
                gravity = Gravity.TOP
                setText(Layouts.customJson(ctx) ?: Layouts.EXAMPLE_JSON)
            }
        val save =
            Button(ctx).apply {
                text = "カスタム配列を保存"
                setOnClickListener {
                    try {
                        edit.error = null
                        Layouts.saveCustom(ctx, edit.text.toString())
                        Toast.makeText(ctx, "保存しました", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        // インラインエラー（Toast は消えて分からなくなるため）
                        edit.error = "JSONエラー: ${e.message}"
                        edit.requestFocus()
                    }
                }
            }

        val box =
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(48), dp(48), dp(48), dp(48))
                addView(
                    TextView(ctx).apply {
                        text = "配列を選択"
                        textSize = 18f
                        labelFor = group.id
                    },
                )
                addView(group)
                addView(
                    TextView(ctx).apply {
                        text = "カスタム配列 (JSON: {\"name\":…,\"rows\":[[\"a\",…],…]} )"
                        textSize = 18f
                        setPadding(0, dp(32), 0, dp(8))
                        labelFor = edit.id
                    },
                )
                addView(edit)
                addView(save)
            }
        setContentView(ScrollView(ctx).apply { addView(box) })
    }
}
