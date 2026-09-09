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

    private const val HIRA_KATA_SHIFT = 0x60
    private const val POP_LIMIT = 20_000 // A* の暴走保険

    /** 探索に渡すグラフ状態の束 */
    private data class Lattice(
        val byStart: Array<ArrayList<Node>>,
        val best: IntArray,
        val len: Int,
        val n: Int,
    )

    fun nbest(
        reading: String,
        source: Source,
        n: Int,
    ): List<String> {
        val len = reading.length
        if (len == 0) return emptyList()
        val byStart = buildNodes(reading, source)
        val lattice = Lattice(byStart, costToEnd(byStart, len), len, n)
        return search(initialPaths(lattice), lattice)
    }

    /** 位置ごとに辞書ノードを張る。単語が見つからない位置はフォールバックで必ず接続。 */
    private fun buildNodes(
        reading: String,
        source: Source,
    ): Array<ArrayList<Node>> {
        val len = reading.length
        val byStart = Array(len) { ArrayList<Node>() }
        for (i in 0 until len) {
            for (l in 1..minOf(MAX_TOKEN, len - i)) {
                byStart[i] += source.lookup(reading.substring(i, i + l)).map { Node(i, i + l, it.word, it.cost) }
            }
            if (byStart[i].none { it.end == i + 1 }) {
                byStart[i].add(Node(i, i + 1, reading[i].toString(), FALLBACK_COST))
            }
        }
        return byStart
    }

    /** best[i]: i から末尾までの最小コスト */
    private fun costToEnd(
        byStart: Array<ArrayList<Node>>,
        len: Int,
    ): IntArray {
        val best = IntArray(len + 1)
        for (i in len - 1 downTo 0) {
            var m = Int.MAX_VALUE
            for (node in byStart[i]) m = minOf(m, node.cost + BOUNDARY_COST + best[node.end])
            best[i] = m
        }
        return best
    }

    private fun initialPaths(lattice: Lattice): PriorityQueue<Path> {
        val pq = PriorityQueue<Path>(compareBy { it.f })
        for (node in lattice.byStart[0]) {
            val g = node.cost + BOUNDARY_COST
            pq.add(Path(g + lattice.best[node.end], g, node, null))
        }
        return pq
    }

    private fun surface(cur: Path): String {
        val words = ArrayDeque<String>()
        var p: Path? = cur
        while (p != null) {
            words.addFirst(p.node.word)
            p = p.prev
        }
        return words.joinToString("")
    }

    private fun expand(
        pq: PriorityQueue<Path>,
        cur: Path,
        lattice: Lattice,
    ) {
        for (next in lattice.byStart[cur.node.end]) {
            val g = cur.g + next.cost + BOUNDARY_COST
            pq.add(Path(g + lattice.best[next.end], g, next, cur))
        }
    }

    private fun search(
        pq: PriorityQueue<Path>,
        lattice: Lattice,
    ): List<String> {
        val out = ArrayList<String>(lattice.n)
        val seen = HashSet<String>()
        var pops = 0
        while (pq.isNotEmpty() && out.size < lattice.n) {
            if (pops++ >= POP_LIMIT) break
            val cur = pq.poll()
            if (cur.node.end == lattice.len) {
                collect(cur, seen, out)
            } else {
                expand(pq, cur, lattice)
            }
        }
        return out
    }

    private fun collect(
        cur: Path,
        seen: HashSet<String>,
        out: ArrayList<String>,
    ) {
        val s = surface(cur)
        if (seen.add(s)) out.add(s)
    }

    /** ひらがな → カタカナ（ー、゛゜などはそのまま） */
    fun hira2kata(s: String): String =
        s
            .map { c ->
                if (c in 'ぁ'..'ゖ') c + HIRA_KATA_SHIFT else c
            }.joinToString("")
}
