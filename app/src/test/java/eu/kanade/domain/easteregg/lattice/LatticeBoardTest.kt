package eu.kanade.domain.easteregg.lattice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты на СЛУЖЕБНОЙ плате-пустышке. Реальная плата живёт только внутри
 * зашифрованного ваулта и в тестах НЕ фигурирует.
 */
class LatticeBoardTest {

    private fun lineBoard(rotation: Int) = LatticeBoard(
        radius = 1,
        port = LatticePort(1, 0, 0),
        cells = mapOf((1 to 0) to LatticeCell(1, 0, LatticeSegment.LINE, rotation)),
    )

    @Test
    fun closedWhenLineBridgesPortAndCore() {
        val circuit = lineBoard(0).evaluate()
        assertTrue(circuit.closed)
        assertTrue(circuit.stubs.isEmpty())
    }

    @Test
    fun notClosedWhenSegmentRotatedAway() {
        assertFalse(lineBoard(1).evaluate().closed)
    }

    @Test
    fun stubDetectedForDanglingConnector() {
        val board = LatticeBoard(
            radius = 1,
            port = LatticePort(1, 0, 0),
            cells = mapOf((1 to 0) to LatticeCell(1, 0, LatticeSegment.CURVE, 0)),
        )
        val circuit = board.evaluate()
        assertFalse(circuit.closed)
        assertTrue((1 to 0) in circuit.stubs)
    }

    @Test
    fun rotationSerializationRoundTrip() {
        val board = lineBoard(0)
        board.rotate(1, 0)
        val serialized = board.serializeRotations()
        assertEquals("1,0:1", serialized)
        val restored = lineBoard(0)
        restored.restoreRotations(serialized)
        assertEquals(1, restored.cells[1 to 0]!!.rotation)
    }

    @Test
    fun effectiveRotationsFoldBySymmetry() {
        val board = lineBoard(0)
        repeat(4) { board.rotate(1, 0) } // raw = 4, line symmetry = 3 -> eff 1
        assertEquals(Triple(1, 0, 1), board.effectiveRotations().single())
    }
}
