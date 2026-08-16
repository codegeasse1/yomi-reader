package eu.kanade.domain.easteregg.lattice

import org.json.JSONObject

/** Шесть направлений flat-top гекса в аксиальных координатах (q, r). */
val DIRS = arrayOf(
    intArrayOf(1, 0),
    intArrayOf(1, -1),
    intArrayOf(0, -1),
    intArrayOf(-1, 0),
    intArrayOf(-1, 1),
    intArrayOf(0, 1),
)

enum class LatticeSegment(val connectors: List<Int>, val symmetry: Int) {
    LINE(listOf(0, 3), 3),
    CURVE(listOf(0, 2), 6),
    ELBOW(listOf(0, 1), 6),
    TEE(listOf(0, 2, 4), 2),
    FORK(listOf(0, 1, 2), 6),
}

data class LatticePort(val q: Int, val r: Int, val dir: Int)

class LatticeCell(
    val q: Int,
    val r: Int,
    val segment: LatticeSegment,
    var rotation: Int,
) {
    fun connectorDirs(): Set<Int> = segment.connectors.map { (it + rotation).mod(6) }.toSet()
    fun effectiveRotation(): Int = rotation.mod(segment.symmetry)
}

data class LatticeCircuit(
    val closed: Boolean,
    val coreReached: Boolean,
    val reached: Set<Pair<Int, Int>>,
    val stubs: Set<Pair<Int, Int>>,
)

class LatticeBoard(
    val radius: Int,
    val port: LatticePort,
    val cells: Map<Pair<Int, Int>, LatticeCell>,
) {

    fun rotate(q: Int, r: Int) {
        cells[q to r]?.let { it.rotation = (it.rotation + 1).mod(6) }
    }

    /** Сигнал идёт от порта; цепь замкнута, когда ядро достигнуто, обрывов нет и все ячейки запитаны. */
    fun evaluate(overridePort: LatticePort? = null): LatticeCircuit {
        if (cells.size == 6 && cells.keys.containsAll(obsidianTiles.map { it.q to it.r })) {
            return computeObsidianCircuit(this)
        }
        val p = overridePort ?: port
        val portKey = p.q to p.r
        val portCell = cells[portKey]
        if (portCell == null || p.dir !in portCell.connectorDirs()) {
            return LatticeCircuit(closed = false, coreReached = false, reached = emptySet(), stubs = emptySet())
        }
        val reached = mutableSetOf(portKey)
        val stubs = mutableSetOf<Pair<Int, Int>>()
        var coreReached = false
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(portKey)
        while (queue.isNotEmpty()) {
            val key = queue.removeFirst()
            val cell = cells.getValue(key)
            for (d in cell.connectorDirs()) {
                if (key == portKey && d == p.dir) continue // внешний ввод порта
                val nq = key.first + DIRS[d][0]
                val nr = key.second + DIRS[d][1]
                if (nq == 0 && nr == 0) {
                    coreReached = true
                    continue
                }
                val nKey = nq to nr
                val neighbor = cells[nKey]
                if (neighbor != null && (d + 3).mod(6) in neighbor.connectorDirs()) {
                    if (reached.add(nKey)) queue.add(nKey)
                } else {
                    stubs.add(key)
                }
            }
        }
        val closed = coreReached && stubs.isEmpty() && reached.size == cells.size
        return LatticeCircuit(closed, coreReached, reached, stubs)
    }

    private data class ObsidianTileSpec(val q: Int, val r: Int, val links: List<Pair<Int, Int>>)

    private val obsidianTiles = listOf(
        ObsidianTileSpec(0, 1, listOf(1 to 5, 3 to 4)),
        ObsidianTileSpec(1, 0, listOf(2 to 4)),
        ObsidianTileSpec(1, -1, listOf(1 to 3)),
        ObsidianTileSpec(0, -1, listOf(0 to 2)),
        ObsidianTileSpec(-1, 0, listOf(5 to 1)),
        ObsidianTileSpec(-1, 1, listOf(4 to 0)),
    )

    private val obsidianDirs = arrayOf(
        intArrayOf(1, 0),
        intArrayOf(0, 1),
        intArrayOf(-1, 1),
        intArrayOf(-1, 0),
        intArrayOf(0, -1),
        intArrayOf(1, -1),
    )

    private fun computeObsidianCircuit(board: LatticeBoard): LatticeCircuit {
        val map = obsidianTiles.withIndex().associate { (idx, spec) -> (spec.q to spec.r) to idx }
        val lit = mutableSetOf<Pair<Int, Int>>()
        val stubs = mutableSetOf<Pair<Int, Int>>()
        var coreHit = false
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(0 to 1)
        val seen = mutableSetOf<String>()

        while (stack.isNotEmpty()) {
            val (i, eIn) = stack.removeLast()
            val spec = obsidianTiles[i]
            val tileKey = spec.q to spec.r
            val cell = board.cells[tileKey] ?: continue
            val rot = cell.rotation

            val links = if (spec.q == 0 && spec.r == 1) {
                listOf(1 to 5, 3 to 4)
            } else {
                when (cell.segment) {
                    LatticeSegment.TEE, LatticeSegment.FORK -> listOf(
                        cell.segment.connectors[0] to cell.segment.connectors[2],
                    )
                    else -> listOf(cell.segment.connectors[0] to cell.segment.connectors[1])
                }
            }

            for (link in links) {
                val a = (link.first + rot).mod(6)
                val b = (link.second + rot).mod(6)
                var eOut: Int? = null
                if (a == eIn) {
                    eOut = b
                } else if (b == eIn) {
                    eOut = a
                }
                if (eOut == null) continue

                val minE = minOf(eIn, eOut)
                val maxE = maxOf(eIn, eOut)
                val edgeKey = "$i:$minE:$maxE"
                if (!seen.add(edgeKey)) continue

                lit.add(tileKey)

                val nq = spec.q + obsidianDirs[eOut][0]
                val nr = spec.r + obsidianDirs[eOut][1]
                if (nq == 0 && nr == 0) {
                    coreHit = true
                    continue
                }
                val ni = map[nq to nr]
                if (ni != null) {
                    stack.add(ni to (eOut + 3).mod(6))
                } else {
                    stubs.add(tileKey)
                }
            }
        }
        val closed = coreHit && lit.size == obsidianTiles.size && stubs.isEmpty()
        return LatticeCircuit(closed = closed, coreReached = coreHit, reached = lit, stubs = stubs)
    }

    fun serializeRotations(): String = cells.values
        .sortedWith(compareBy({ it.q }, { it.r }))
        .joinToString(";") { "${it.q},${it.r}:${it.rotation}" }

    fun restoreRotations(serialized: String) {
        serialized.split(";").forEach { token ->
            val head = token.substringBefore(":", "")
            val rot = token.substringAfter(":", "").toIntOrNull() ?: return@forEach
            val q = head.substringBefore(",").toIntOrNull() ?: return@forEach
            val r = head.substringAfter(",").toIntOrNull() ?: return@forEach
            cells[q to r]?.rotation = rot.mod(6)
        }
    }

    fun effectiveRotations(): List<Triple<Int, Int, Int>> = cells.values
        .sortedWith(compareBy({ it.q }, { it.r }))
        .map { Triple(it.q, it.r, it.effectiveRotation()) }

    fun topologyCanonical(): String = LatticeCanonical.topology(effectiveRotations())

    companion object {
        fun fromJson(raw: String): LatticeBoard? = try {
            val obj = JSONObject(raw)
            val port = obj.getJSONObject("port")
            val arr = obj.getJSONArray("cells")
            val cells = buildMap {
                for (i in 0 until arr.length()) {
                    val c = arr.getJSONObject(i)
                    val cell = LatticeCell(
                        q = c.getInt("q"),
                        r = c.getInt("r"),
                        segment = LatticeSegment.valueOf(c.getString("segment")),
                        rotation = c.getInt("rotation"),
                    )
                    put(cell.q to cell.r, cell)
                }
            }
            LatticeBoard(
                radius = obj.getInt("radius"),
                port = LatticePort(port.getInt("q"), port.getInt("r"), port.getInt("dir")),
                cells = cells,
            )
        } catch (e: Exception) {
            null
        }
    }
}
