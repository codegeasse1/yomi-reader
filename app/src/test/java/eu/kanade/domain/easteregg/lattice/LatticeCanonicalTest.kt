package eu.kanade.domain.easteregg.lattice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatticeCanonicalTest {

    @Test
    fun carriersCanonicalIncludesFrameWhenPresent() {
        val canon = LatticeCanonical.carriers(1, 2, 3, frame = "deadbeef")
        assertEquals("carriers/v2|p:deadbeef|a:1|m:2|n:3", canon)
    }

    @Test
    fun carriersCanonicalFallsBackToV1WithoutFrame() {
        assertEquals("carriers/v1|a:1|m:2|n:3", LatticeCanonical.carriers(1, 2, 3, frame = ""))
    }

    @Test
    fun carriersCanonicalUsesVaultFrameByDefault() {
        val canon = LatticeCanonical.carriers(1, 2, 3)
        assertTrue(canon.startsWith("carriers/v2|p:"))
        assertTrue(canon.endsWith("|a:1|m:2|n:3"))
        assertTrue(canon.contains(LatticeVaultData.FRAME))
    }

    @Test
    fun topologyCanonicalSortsCellsByQThenR() {
        val canon = LatticeCanonical.topology(
            listOf(Triple(1, -1, 0), Triple(-1, 0, 5), Triple(0, 1, 2)),
        )
        assertEquals("topology/v1|-1,0:5;0,1:2;1,-1:0", canon)
    }
}
