package com.faraon.blockblast

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

const val N = 8

data class Piece(val cells: List<Pair<Int, Int>>, val color: Int) {
    val h = cells.maxOf { it.first } + 1
    val w = cells.maxOf { it.second } + 1
}

private fun p(vararg rows: String) = rows.flatMapIndexed { r, s ->
    s.mapIndexedNotNull { c, ch -> if (ch == '#') r to c else null }
}

object Shapes {
    val all = listOf(
        p("#"), p("##"), p("###"), p("####"), p("#####"),
        p("#", "#"), p("#", "#", "#"), p("#", "#", "#", "#"), p("#", "#", "#", "#", "#"),
        p("##", "##"), p("###", "###", "###"), p("###", "###"), p("##", "##", "##"),
        p("##", "#"), p("##", " #"), p("#", "##"), p(" #", "##"),
        p("#", "#", "##"), p(" #", " #", "##"), p("##", "#", "#"), p("##", " #", " #"),
        p("#", "#", "###"), p("###", "#", "#"), p("###", "  #", "  #"), p("  #", "  #", "###"),
        p("###", " # "), p(" # ", "###"), p("#", "##", "#"), p(" #", "##", " #"),
        p(" ##", "## "), p("## ", " ##"), p("#", "##", " #"), p(" #", "##", "#")
    )
}

class GameState(private val prefs: SharedPreferences) {
    var grid by mutableStateOf(List(N * N) { -1 }); private set
    var pieces by mutableStateOf<List<Piece?>>(emptyList()); private set
    var score by mutableIntStateOf(0); private set
    var best by mutableIntStateOf(prefs.getInt("best", 0)); private set
    var combo by mutableIntStateOf(0); private set
    var clearing by mutableStateOf<Set<Int>>(emptySet()); private set
    var over by mutableStateOf(false); private set
    private var sinceClear = 0

    init { reset() }

    fun reset() {
        grid = List(N * N) { -1 }
        score = 0; combo = 0; sinceClear = 0
        over = false; clearing = emptySet()
        pieces = deal()
    }

    fun fits(p: Piece, r: Int, c: Int): Boolean = p.cells.all { (dr, dc) ->
        val rr = r + dr
        val cc = c + dc
        rr in 0 until N && cc in 0 until N && grid[rr * N + cc] < 0
    }

    private fun canPlaceAny(p: Piece) = (0 until N).any { r -> (0 until N).any { c -> fits(p, r, c) } }

    private fun newPiece() = Piece(Shapes.all.random(), Random.nextInt(7))

    private fun deal(): List<Piece?> {
        repeat(60) {
            val l = List(3) { newPiece() }
            if (l.any { canPlaceAny(it) }) return l
        }
        return List(3) { newPiece() }
    }

    fun place(i: Int, r: Int, c: Int): Boolean {
        val p = pieces.getOrNull(i) ?: return false
        if (clearing.isNotEmpty() || over || !fits(p, r, c)) return false
        val g = grid.toMutableList()
        p.cells.forEach { (dr, dc) -> g[(r + dr) * N + c + dc] = p.color }
        var gain = p.cells.size
        val full = mutableSetOf<Int>()
        var lines = 0
        for (k in 0 until N) {
            if ((0 until N).all { g[k * N + it] >= 0 }) { lines++; (0 until N).forEach { full += k * N + it } }
            if ((0 until N).all { g[it * N + k] >= 0 }) { lines++; (0 until N).forEach { full += it * N + k } }
        }
        if (lines > 0) {
            combo++; sinceClear = 0
            gain += lines * lines * 10 + (combo - 1) * 10
        } else if (++sinceClear >= 3) combo = 0
        grid = g
        score += gain
        if (score > best) { best = score; prefs.edit().putInt("best", best).apply() }
        pieces = pieces.toMutableList().also { it[i] = null }
        if (full.isEmpty()) afterMove() else clearing = full
        return true
    }

    fun finishClear() {
        val g = grid.toMutableList()
        clearing.forEach { g[it] = -1 }
        grid = g
        clearing = emptySet()
        afterMove()
    }

    private fun afterMove() {
        if (pieces.all { it == null }) pieces = deal()
        if (pieces.filterNotNull().none { canPlaceAny(it) }) over = true
    }
}
