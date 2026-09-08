package dev.example.jpkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider

/** 配列データを描画するだけの自前ソフトキーボード View。 */
class KeyboardView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        var onKey: (String) -> Unit = {}
        var onBackspace: () -> Unit = {}
        var onEnter: () -> Unit = {}
        var onSpace: () -> Unit = {}
        var onSwitchLayout: () -> Unit = {}

        var layout: Layout = Layouts.BUILTIN[0]
            set(value) {
                field = value
                if (width > 0) recompute()
                requestLayout()
                invalidate()
            }

        private val d = resources.displayMetrics.density
        private val gap = 6 * d // 隣接タップターゲット間の余白
        private val bgPaint = Paint().apply { color = 0xFF1B1B1F.toInt() }
        private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2D2F31.toInt() }
        private val funcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A3D40.toInt() }
        private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5A5F64.toInt() }
        private val borderPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1 * d
                color = 0xFF4A4E52.toInt()
            }
        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE3E3E3.toInt()
                textAlign = Paint.Align.CENTER
                textSize = 20 * d
            }

        // act: 0=文字キー, 1=配列切替, 2=空白/変換, 3=確定, 4=バックスペース
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
        private var pressed = -1 // 押下中の Hit インデックス（-1=なし）

        private val handler = Handler(Looper.getMainLooper())

        // ponytail: バックスペース連打間隔は固定(400ms後に60ms間隔)。速度設定が必要になったらフィールド化
        private val repeat =
            object : Runnable {
                override fun run() {
                    onBackspace()
                    handler.postDelayed(this, 60)
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

        override fun onMeasure(
            widthSpec: Int,
            heightSpec: Int,
        ) {
            // IME ウィンドウに引き伸ばされないよう、内容に応じた高さを返す
            val keyH = 44 * d
            val pad = 2 * d
            val h = (pad * 2 + keyH * layout.rows.size + gap * (layout.rows.size + 1) + 48 * d + bottomInset).toInt()
            setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthSpec), h)
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            ow: Int,
            oh: Int,
        ) = recompute()

        private fun recompute() {
            val pad = 2 * d
            val funcH = 48 * d
            val bottom = height - bottomInset // ジェスチャー領域を避ける
            val rowH = (bottom - funcH - gap * (layout.rows.size + 1)) / layout.rows.size
            val list = mutableListOf<Hit>()
            var y = pad
            for (row in layout.rows) {
                val kw = (width - pad * 2 - gap * (row.keys.size - 1)) / row.keys.size
                var x = pad
                for (k in row.keys) {
                    list.add(Hit(x, y, x + kw, y + rowH, k.label, k.out, 0))
                    x += kw + gap
                }
                y += rowH + gap
            }
            val fw = (width - pad * 2 - gap * 3)
            val w1 = fw * 0.18f
            val w2 = fw * 0.46f
            val w3 = fw * 0.18f
            var x = pad
            y = bottom - funcH
            list.add(Hit(x, y, x + w1, y + funcH, "配列", null, 1))
            x += w1 + gap
            list.add(Hit(x, y, x + w2, y + funcH, "空白/変換", null, 2))
            x += w2 + gap
            list.add(Hit(x, y, x + w3, y + funcH, "確定", null, 3))
            x += w3 + gap
            list.add(Hit(x, y, x + fw * 0.18f, y + funcH, "⌫", null, 4))
            hits = list
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            hits.forEachIndexed { i, h ->
                val fill =
                    when {
                        i == pressed -> pressedPaint
                        h.act == 0 -> keyPaint
                        else -> funcPaint
                    }
                canvas.drawRoundRect(h.l, h.t + 1, h.r, h.b - 1, 6 * d, 6 * d, fill)
                canvas.drawRoundRect(h.l, h.t + 1, h.r, h.b - 1, 6 * d, 6 * d, borderPaint)
                canvas.drawText(
                    h.label,
                    (h.l + h.r) / 2,
                    (h.t + h.b) / 2 - (textPaint.ascent() + textPaint.descent()) / 2,
                    textPaint,
                )
            }
        }

        private fun hitAt(
            x: Float,
            y: Float,
        ) = hits.indexOfFirst { x in it.l..it.r && y in it.t..it.b }

        private fun fire(h: Hit) {
            when (h.act) {
                0 -> onKey(h.out!!)
                1 -> onSwitchLayout()
                2 -> onSpace()
                3 -> onEnter()
                4 -> onBackspace()
            }
        }

        private fun setPressed(i: Int) {
            if (i != pressed) {
                pressed = i
                invalidate()
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val i = hitAt(e.x, e.y)
                    if (i != pressed) {
                        handler.removeCallbacks(repeat)
                        setPressed(i)
                        // バックスペースは押した瞬間に1回 + 押下中リピート
                        if (i >= 0 && hits[i].act == 4) {
                            onBackspace()
                            handler.postDelayed(repeat, 400)
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(repeat)
                    val i = pressed
                    setPressed(-1)
                    if (i >= 0 && hits[i].act != 4) fire(hits[i]) // バックスペースは DOWN済み
                }

                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(repeat)
                    setPressed(-1)
                }

                else -> {
                    return false
                }
            }
            return true
        }

        // --- TalkBack 対応: 各キーを仮想ノードとして公開 ---

        private inner class KbdNodeProvider : AccessibilityNodeProvider() {
            override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
                if (virtualViewId == UNDEFINED_ID) {
                    val n = AccessibilityNodeInfo.obtain(this@KeyboardView)
                    hits.indices.forEach { n.addChild(this@KeyboardView, it) }
                    return n
                }
                val h = hits.getOrNull(virtualViewId) ?: return null
                val node = AccessibilityNodeInfo.obtain(this@KeyboardView, virtualViewId)
                node.className = "android.widget.Button"
                node.contentDescription = if (h.label == "⌫") "削除" else h.label
                node.isClickable = true
                node.isEnabled = isEnabled
                node.isVisibleToUser = isShown
                node.setParent(this@KeyboardView)
                node.setBoundsInParent(Rect(h.l.toInt(), h.t.toInt(), h.r.toInt(), h.b.toInt()))
                val loc = IntArray(2)
                this@KeyboardView.getLocationOnScreen(loc)
                node.setBoundsInScreen(
                    Rect(
                        loc[0] + h.l.toInt(),
                        loc[1] + h.t.toInt(),
                        loc[0] + h.r.toInt(),
                        loc[1] + h.b.toInt(),
                    ),
                )
                node.addAction(AccessibilityNodeInfo.ACTION_CLICK)
                return node
            }

            override fun performAction(
                virtualViewId: Int,
                action: Int,
                arguments: Bundle?,
            ): Boolean {
                val h = hits.getOrNull(virtualViewId) ?: return false
                return when (action) {
                    AccessibilityNodeInfo.ACTION_CLICK -> {
                        fire(h)
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
        }

        private val provider = KbdNodeProvider()

        override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = provider

        private companion object {
            // ルート仮想ノードID（AccessibilityNodeProvider.UNDEFINED_ITEM_ID と同値）
            const val UNDEFINED_ID = View.NO_ID
        }
    }
