package dev.example.jpkeyboard

import java.util.PriorityQueue

/**
 * かな読み → 文章候補の N-best 生成。
 * ユニグラムコスト（Mozc辞書の単語コスト + 単語境界ペナルティ）の Viterbi + A*。
 * 辞書にない1文字はフォールバックノードで必ず接続性を保証する。
 */
object Converter {
    const val BOUNDARY_COST = 100
    const val FALLBACK_COST = 5000
    const val MAX_TOKEN = 16

    data class Word(
        val word: String,
        val cost: Int,
    )

    fun interface Source {
        fun lookup(reading: String): List<Word>
    }

    data class Node(
        val start: Int,
        val end: Int,
        val word: String,
        val cost: Int,
    )

    private data class Path(
        val f: Int,
        val g: Int,
        val node: Node,
        val prev: Path?,
    )

    fun nbest(
        reading: String,
        source: Source,
        n: Int,
    ): List<String> {
        val len = reading.length
        if (len == 0) return emptyList()

        val byStart = Array(len) { ArrayList<Node>() }
        for (i in 0 until len) {
            for (l in 1..minOf(MAX_TOKEN, len - i)) {
                for (w in source.lookup(reading.substring(i, i + l))) {
                    byStart[i].add(Node(i, i + l, w.word, w.cost))
                }
            }
            if (byStart[i].none { it.end == i + 1 }) {
                byStart[i].add(Node(i, i + 1, reading[i].toString(), FALLBACK_COST))
            }
        }

        val best = IntArray(len + 1) // best[i]: i から末尾までの最小コスト
        for (i in len - 1 downTo 0) {
            var m = Int.MAX_VALUE
            for (node in byStart[i]) m = minOf(m, node.cost + BOUNDARY_COST + best[node.end])
            best[i] = m
        }

        val pq = PriorityQueue<Path>(compareBy { it.f })
        for (node in byStart[0]) {
            val g = node.cost + BOUNDARY_COST
            pq.add(Path(g + best[node.end], g, node, null))
        }
        val out = ArrayList<String>(n)
        val seen = HashSet<String>()
        var pops = 0
        while (pq.isNotEmpty() && out.size < n && pops++ < 20000) {
            val cur = pq.poll()
            if (cur.node.end == len) {
                val words = ArrayDeque<String>()
                var p: Path? = cur
                while (p != null) {
                    words.addFirst(p.node.word)
                    p = p.prev
                }
                val surface = words.joinToString("")
                if (seen.add(surface)) out.add(surface)
                continue
            }
            for (next in byStart[cur.node.end]) {
                val g = cur.g + next.cost + BOUNDARY_COST
                pq.add(Path(g + best[next.end], g, next, cur))
            }
        }
        return out
    }

    /** ひらがな → カタカナ（ー、゛゜などはそのまま） */
    fun hira2kata(s: String): String =
        s
            .map { c ->
                if (c in 'ぁ'..'ゖ') c + 0x60 else c
            }.joinToString("")
}
