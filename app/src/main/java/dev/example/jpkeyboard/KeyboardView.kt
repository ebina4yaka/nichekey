package dev.example.jpkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets

/** 自前ソフトキーボード View。かな(ローマ字)/英字パレット切替・シフト・記号ページ・キーリピート付き。 */
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

        var layout: Layout = Layouts.BUILTIN[0]
            set(value) {
                field = value
                page = Page.MAIN
                if (width > 0) recompute()
                requestLayout()
                invalidate()
            }

        private enum class Page { MAIN, SYMBOL }

        private enum class Mode { KANA, EN }

        // 機能キーのアクション種別（数字コードを排して列挙型に）
        private enum class Act { KEY, MODE, SHIFT, SPACE, SYMBOL, COMMIT, BACKSPACE }

        private companion object {
            // 機能バーの並び順と幅の比率（KEY 以外の順）
            val FUNC_ACTS = listOf(Act.MODE, Act.SHIFT, Act.SPACE, Act.SYMBOL, Act.COMMIT, Act.BACKSPACE)
            val FUNC_WIDTHS = listOf(0.14f, 0.12f, 0.32f, 0.12f, 0.15f, 0.15f)
            const val KEY_H_DP = 44
            const val GAP_DP = 3
            const val PAD_DP = 2
            const val FUNC_H_DP = 48
            const val TEXT_SIZE_DP = 20
            const val CORNER_DP = 6
            const val REPEAT_START_MS = 400L
            const val REPEAT_INTERVAL_MS = 50L
            const val POPUP_SCALE = 1.6f // プレビューの幅倍率
            const val POPUP_H_SCALE = 1.3f // プレビューの高さ倍率
            const val POPUP_RISE_SCALE = 1.3f // キー高さ比でどれだけ上に浮かせるか
            const val POPUP_TEXT_SCALE = 1.6f
            const val COLOR_BG = 0xFF1B1B1F.toInt()
            const val COLOR_KEY = 0xFF2D2F31.toInt()
            const val COLOR_FUNC = 0xFF3A3D40.toInt()
            const val COLOR_ACTIVE = 0xFF2A5A5A.toInt()
            const val COLOR_TEXT = 0xFFE3E3E3.toInt()
        }

        private var page = Page.MAIN
        private var mode = Mode.KANA
        private var shift = false

        private val d = resources.displayMetrics.density
        private val bgPaint = Paint().apply { color = COLOR_BG }
        private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_KEY }
        private val funcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_FUNC }
        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_ACTIVE }
        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT
                textAlign = Paint.Align.CENTER
                textSize = TEXT_SIZE_DP * d
            }
        private val popupPaint = Paint(textPaint).apply { textSize = TEXT_SIZE_DP * POPUP_TEXT_SCALE * d }

        // act: どの機能キーか（Act 列挙型）
        private data class Hit(
            val l: Float,
            val t: Float,
            val r: Float,
            val b: Float,
            val label: String,
            val out: String?,
            val act: Act,
            /** true ならローマ字変換に渡さず直接コミット（数字行など） */
            val direct: Boolean = false,
        )

        private var hits: List<Hit> = emptyList()
        private var bottomInset = 0

        /** 押下中のキー（拡大プレビュー表示・スライド追従用） */
        private var pressed: Hit? = null
        private var repeating = false

        private val repeatAction =
            object : Runnable {
                override fun run() {
                    onBackspace()
                    postDelayed(this, REPEAT_INTERVAL_MS)
                }
            }

        /** ⌫ 長押しで発火: 1回削除して連続リピートへ */
        private val longPress =
            object : Runnable {
                override fun run() {
                    repeating = true
                    onBackspace()
                    postDelayed(repeatAction, REPEAT_INTERVAL_MS)
                }
            }

        init {
            // ジェスチャーナビ領域（画面最下部）とキーがかぶらないようにする
            setOnApplyWindowInsetsListener { _, insets ->
                val b = navInset(insets)
                if (b != bottomInset) {
                    bottomInset = b
                    // 高さ(bottomInset込み)を再計測 → onSizeChanged → recompute で反映
                    requestLayout()
                }
                insets
            }
        }

        private fun navInset(insets: WindowInsets): Int =
            insets
                .getInsets(
                    WindowInsets.Type.systemGestures() or
                        WindowInsets.Type.mandatorySystemGestures() or
                        WindowInsets.Type.navigationBars(),
                ).bottom

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            // 初回計測で insets リスナーより先に確定させる（初回起動時の OS バー重なり防止）
            val b = rootWindowInsets?.let { navInset(it) } ?: 0
            if (b != bottomInset) {
                bottomInset = b
                requestLayout()
            }
        }

        private fun currentLayout(): Layout =
            when {
                page == Page.SYMBOL -> Layouts.SYMBOL
                else -> Layouts.withNumberRow(layout) // かな/英字とも選択配列 + 数字行
            }

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
            val keyH = KEY_H_DP * d
            val gap = GAP_DP * d
            val pad = PAD_DP * d
            val rows = currentLayout().rows.size
            val h = (pad * 2 + keyH * rows + gap * (rows + 1) + FUNC_H_DP * d + bottomInset).toInt()
            setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthSpec), h)
        }

        private fun recompute() {
            val gap = GAP_DP * d
            val pad = PAD_DP * d
            val funcH = FUNC_H_DP * d
            val bottom = height - bottomInset // ジェスチャー領域を避ける
            val rows = currentLayout().rows
            val rowH = (bottom - funcH - gap * (rows.size + 1)) / rows.size
            hits = keyHits(rows, pad, gap, rowH) + funcHits(bottom.toFloat(), funcH, pad, gap)
        }

        private fun keyLabel(k: Key): String = if (shift) k.out.uppercase() else k.label

        private fun keyHits(
            rows: List<Row>,
            pad: Float,
            gap: Float,
            rowH: Float,
        ): List<Hit> {
            val list = mutableListOf<Hit>()
            var y = pad
            for (row in rows) {
                val kw = (width - pad * 2 - gap * (row.keys.size - 1)) / row.keys.size
                var x = pad
                for (k in row.keys) {
                    list.add(Hit(x, y, x + kw, y + rowH, keyLabel(k), k.out, Act.KEY, k.direct))
                    x += kw + gap
                }
                y += rowH + gap
            }
            return list
        }

        private fun funcHits(
            bottom: Float,
            funcH: Float,
            pad: Float,
            gap: Float,
        ): List<Hit> {
            val y = bottom - funcH
            val fw = width - pad * 2 - gap * (FUNC_ACTS.size - 1)
            val labels =
                listOf(
                    if (mode == Mode.KANA) "英字" else "かな",
                    "⇧",
                    "空白/変換",
                    if (page == Page.SYMBOL) "あA" else "?123",
                    "確定",
                    "⌫",
                )
            val list = mutableListOf<Hit>()
            var x = pad
            for ((i, act) in FUNC_ACTS.withIndex()) {
                val w = FUNC_WIDTHS[i]
                list.add(Hit(x, y, x + fw * w, y + funcH, labels[i], null, act))
                x += fw * w + gap
            }
            return list
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            for (h in hits) {
                val paint =
                    when {
                        h.act == Act.SHIFT && shift -> activePaint
                        h.act == Act.SYMBOL && page == Page.SYMBOL -> activePaint
                        h.act == Act.KEY -> keyPaint
                        else -> funcPaint
                    }
                canvas.drawRoundRect(h.l, h.t + 1, h.r, h.b - 1, CORNER_DP * d, CORNER_DP * d, paint)
                canvas.drawText(
                    h.label,
                    (h.l + h.r) / 2,
                    (h.t + h.b) / 2 - (textPaint.ascent() + textPaint.descent()) / 2,
                    textPaint,
                )
            }
            pressed?.let { drawPopup(canvas, it) }
        }

        /** 押下中キーを上方向に拡大表示 */
        private fun drawPopup(
            canvas: Canvas,
            h: Hit,
        ) {
            val w = (h.r - h.l) * POPUP_SCALE
            val keyH = h.b - h.t
            val x = (h.l + h.r) / 2 - w / 2
            val left = x.coerceIn(0f, width - w)
            val top = (h.t - keyH * POPUP_RISE_SCALE).coerceAtLeast(PAD_DP * d) // 最上段は画面内にクランプ
            val bottom = top + keyH * POPUP_H_SCALE
            val paint = if (h.act == Act.KEY) keyPaint else funcPaint
            canvas.drawRoundRect(left, top, left + w, bottom, CORNER_DP * d, CORNER_DP * d, paint)
            canvas.drawText(
                h.label,
                left + w / 2,
                (top + bottom) / 2 - (popupPaint.ascent() + popupPaint.descent()) / 2,
                popupPaint,
            )
        }

        /** 押下: 位置を記録して拡大プレビュー表示（⌫ のみ長押しリピートを予約） */
        private fun press(e: MotionEvent) {
            repeating = false
            pressed = hitAt(e)
            if (pressed?.act == Act.BACKSPACE) postDelayed(longPress, REPEAT_START_MS)
            invalidate()
        }

        /** スライド: 移動先のキーに追従（指を離した位置のキーが確定） */
        private fun slide(e: MotionEvent) {
            val h = hitAt(e) ?: return
            if (h == pressed) return
            pressed = h
            if (h.act != Act.BACKSPACE) removeCallbacks(longPress)
            invalidate()
        }

        /** 長押しリピート済みなら追加発火なし。最終位置のキーを発火 */
        private fun release(e: MotionEvent) {
            val h = if (repeating) null else hitAt(e) ?: pressed
            removeCallbacks(longPress)
            removeCallbacks(repeatAction)
            clearTouch()
            h?.let { fire(it) }
        }

        private fun cancel() {
            removeCallbacks(longPress)
            removeCallbacks(repeatAction)
            clearTouch()
            invalidate()
        }

        private fun clearTouch() {
            repeating = false
            pressed = null
        }

        private fun hitAt(e: MotionEvent): Hit? = hits.firstOrNull { e.x in it.l..it.r && e.y in it.t..it.b }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> press(e)
                MotionEvent.ACTION_MOVE -> slide(e)
                MotionEvent.ACTION_UP -> release(e)
                MotionEvent.ACTION_CANCEL -> cancel()
            }
            return true
        }

        private fun fire(h: Hit) {
            when (h.act) {
                Act.KEY -> commitKey(h)
                Act.MODE -> switchMode()
                Act.SHIFT -> toggleShift()
                Act.SPACE -> onSpace()
                Act.SYMBOL -> togglePage()
                Act.COMMIT -> onEnter()
                Act.BACKSPACE -> onBackspace() // タップは1回削除（長押しリピートは longPress が担当）
            }
            invalidate()
        }

        private fun commitKey(h: Hit) {
            val out = h.out ?: return
            onKey(if (shift) out.uppercase() else out, !directOutput(h))
            if (shift) {
                shift = false
                recompute()
            }
        }

        private fun directOutput(h: Hit): Boolean {
            if (h.direct || shift) return true
            return mode == Mode.EN || page != Page.MAIN
        }

        private fun switchMode() {
            // かな ⇄ 英字 切替（行数が変わるので re-measure）
            onEnter() // 未確定のローマ字を確定してから切替
            mode = if (mode == Mode.KANA) Mode.EN else Mode.KANA
            shift = false
            requestLayout()
        }

        private fun toggleShift() {
            shift = !shift
            recompute()
        }

        private fun togglePage() {
            page = if (page == Page.MAIN) Page.SYMBOL else Page.MAIN
            shift = false
            requestLayout()
        }
    }
