package dev.example.jpkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets

/** 配列データを描画するだけの自前ソフトキーボード View。シフト・記号ページ・キーリピート付き。 */
class KeyboardView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        /** (出力テキスト, ローマ字バッファに入れる=true / 直接コミット=false) */
        var onKey: (String, Boolean) -> Unit = { _, _ -> }
        var onBackspace: () -> Unit = {}
        var onEnter: () -> Unit = {}
        var onSpace: () -> Unit = {}
        var onSwitchLayout: () -> Unit = {}

        var layout: Layout = Layouts.BUILTIN[0]
            set(value) {
                field = value
                page = Page.MAIN
                if (width > 0) recompute()
                requestLayout()
                invalidate()
            }

        private enum class Page { MAIN, SYMBOL }

        private var page = Page.MAIN
        private var shift = false

        private val d = resources.displayMetrics.density
        private val bgPaint = Paint().apply { color = 0xFF1B1B1F.toInt() }
        private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2D2F31.toInt() }
        private val funcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A3D40.toInt() }
        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A5A5A.toInt() }
        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE3E3E3.toInt()
                textAlign = Paint.Align.CENTER
                textSize = 20 * d
            }

        // act: 0=文字キー, 1=配列切替, 2=シフト, 3=空白/変換, 4=記号ページ, 5=確定, 6=バックスペース(リピート)
        private data class Hit(
            val l: Float,
            val t: Float,
            val r: Float,
            val b: Float,
            val label: String,
            val out: String?,
            val act: Int,
        )

        private var hits: List<Hit> = emptyList()
        private var bottomInset = 0

        private val repeatAction =
            object : Runnable {
                override fun run() {
                    onBackspace()
                    postDelayed(this, 50)
                }
            }

        init {
            // ジェスチャーナビ領域（画面最下部）とキーがかぶらないようにする
            setOnApplyWindowInsetsListener { v, insets ->
                val b =
                    insets
                        .getInsets(
                            WindowInsets.Type.systemGestures() or
                                WindowInsets.Type.mandatorySystemGestures() or
                                WindowInsets.Type.navigationBars(),
                        ).bottom
                if (b != bottomInset) {
                    bottomInset = b
                    requestLayout()
                    if (width > 0) recompute()
                }
                insets
            }
        }

        private fun currentLayout(): Layout = if (page == Page.SYMBOL) Layouts.SYMBOL else layout

        override fun onSizeChanged(
            w: Int,
            h: Int,
            ow: Int,
            oh: Int,
        ) = recompute()

        override fun onMeasure(
            widthSpec: Int,
            heightSpec: Int,
        ) {
            // IME ウィンドウに引き伸ばされないよう、内容に応じた高さを返す
            val keyH = 44 * d
            val gap = 3 * d
            val pad = 2 * d
            val rows = currentLayout().rows.size
            val h = (pad * 2 + keyH * rows + gap * (rows + 1) + 48 * d + bottomInset).toInt()
            setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthSpec), h)
        }

        private fun recompute() {
            val gap = 3 * d
            val pad = 2 * d
            val funcH = 48 * d
            val bottom = height - bottomInset // ジェスチャー領域を避ける
            val rows = currentLayout().rows
            val rowH = (bottom - funcH - gap * (rows.size + 1)) / rows.size
            val list = mutableListOf<Hit>()
            var y = pad
            for (row in rows) {
                val kw = (width - pad * 2 - gap * (row.keys.size - 1)) / row.keys.size
                var x = pad
                for (k in row.keys) {
                    val label = if (shift) k.out.uppercase() else k.label
                    list.add(Hit(x, y, x + kw, y + rowH, label, k.out, 0))
                    x += kw + gap
                }
                y += rowH + gap
            }
            y = bottom - funcH
            val fw = (width - pad * 2 - gap * 5)
            val w = floatArrayOf(0.14f, 0.12f, 0.32f, 0.12f, 0.15f, 0.15f)
            val labels = arrayOf("配列", "⇧", "空白/変換", if (page == Page.SYMBOL) "あA" else "?123", "確定", "⌫")
            var x = pad
            for (i in w.indices) {
                list.add(Hit(x, y, x + fw * w[i], y + funcH, labels[i], null, i + 1))
                x += fw * w[i] + gap
            }
            hits = list
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            for (h in hits) {
                val paint =
                    when {
                        h.act == 2 && shift -> activePaint
                        h.act == 4 && page == Page.SYMBOL -> activePaint
                        h.act == 0 -> keyPaint
                        else -> funcPaint
                    }
                canvas.drawRoundRect(h.l, h.t + 1, h.r, h.b - 1, 6 * d, 6 * d, paint)
                canvas.drawText(
                    h.label,
                    (h.l + h.r) / 2,
                    (h.t + h.b) / 2 - (textPaint.ascent() + textPaint.descent()) / 2,
                    textPaint,
                )
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val h = hits.firstOrNull { e.x in it.l..it.r && e.y in it.t..it.b } ?: return true
                    fire(h)
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(repeatAction)
                }
            }
            return true
        }

        private fun fire(h: Hit) {
            when (h.act) {
                0 -> {
                    if (page == Page.MAIN && !shift) {
                        onKey(h.out!!, true)
                    } else {
                        onKey(if (shift) h.out!!.uppercase() else h.out!!, false)
                    }
                    if (shift) {
                        shift = false
                        recompute()
                    }
                }

                1 -> {
                    onSwitchLayout()
                }

                2 -> {
                    shift = !shift
                    recompute()
                }

                3 -> {
                    onSpace()
                }

                4 -> {
                    page = if (page == Page.MAIN) Page.SYMBOL else Page.MAIN
                    shift = false
                    requestLayout()
                }

                5 -> {
                    onEnter()
                }

                6 -> {
                    onBackspace()
                    removeCallbacks(repeatAction)
                    postDelayed(repeatAction, 400)
                }
            }
            invalidate()
        }
    }
