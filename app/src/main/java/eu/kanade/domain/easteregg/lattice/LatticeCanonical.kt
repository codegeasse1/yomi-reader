package eu.kanade.domain.easteregg.lattice

/**
 * Канонические строки-ключи для ваулта. Формат байт-в-байт совпадает с
 * tools/lattice_board.mjs (canonicalCarriers / canonicalTopology).
 */
object LatticeCanonical {

    /**
     * Stage A open-key. [frame] — forge-generated fragment (LatticeVaultData.FRAME).
     * Без frame ключ v1 не открывает текущий ваулт.
     */
    fun carriers(anime: Int, manga: Int, novel: Int, frame: String = LatticeVaultData.FRAME): String =
        if (frame.isNotEmpty()) {
            "carriers/v2|p:$frame|a:$anime|m:$manga|n:$novel"
        } else {
            "carriers/v1|a:$anime|m:$manga|n:$novel"
        }

    fun topology(cells: List<Triple<Int, Int, Int>>): String =
        "topology/v1|" + cells
            .sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString(";") { (q, r, rot) -> "$q,$r:$rot" }
}
