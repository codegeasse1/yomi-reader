package eu.kanade.domain.easteregg.lattice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Векторы сгенерированы СЛУЖЕБНЫМ каноном "test/v1|dummy" через тот же пайплайн,
 * что и tools/lattice_forge.mjs. Реальное решение пасхалки в тестах НЕ присутствует.
 */
class LatticeVaultTest {

    private val stage = LatticeStage(
        salt = "nYb+3NGRid6TiSZZuG9bbQ==",
        iv = "pgQpc7DFLObtrRHe",
        check = "Dvwv3g1RLi4MYmBogTXda6VSdIsIRAsZo6tiIoGx0Zo=",
        data = "sjKfM8+k3lZ2qa2YLsIr8m8ZSLSAuFHXMF6M5UNdWg==",
    )

    @Test
    fun opensWithCorrectCanonical() {
        val out = LatticeVault.tryOpen("test/v1|dummy", stage)
        assertEquals("{\"kind\":\"test\"}", out?.decodeToString())
    }

    @Test
    fun silentNullOnWrongCanonical() {
        assertNull(LatticeVault.tryOpen("test/v1|wrong", stage))
    }

    @Test
    fun silentNullOnCorruptedCiphertext() {
        val corrupted = stage.copy(data = stage.data.dropLast(8) + "AAAAAAA=")
        assertNull(LatticeVault.tryOpen("test/v1|dummy", stage.copy(data = corrupted.data)))
    }

    /**
     * Production Stage B must open with the unique closed topology from the forge scenario.
     * Guards PBKDF2 parity (Mac/UTF-8) against Node forge.
     */
    @Test
    fun productionStageBOpensWithKnownTopology() {
        val topology =
            "topology/v1|-1,0:5;-1,1:0;0,-1:4;0,1:1;1,-1:3;1,0:0"
        val raw = LatticeVault.tryOpen(topology, LatticeVaultData.STAGE_B)
        assertEquals(true, raw != null)
        val json = raw!!.decodeToString()
        assertEquals(true, json.contains("lattice_resonance"))
        assertEquals(true, json.contains("LATTICE_PROTOCOL"))
    }

    @Test
    fun productionStageAOpensWithCarrierCanonical() {
        val key = LatticeCanonical.carriers(1, 2, 3)
        val raw = LatticeVault.tryOpen(key, LatticeVaultData.STAGE_A)
        assertEquals(true, raw != null)
        val json = raw!!.decodeToString()
        assertEquals(true, json.contains("\"radius\""))
        assertEquals(true, json.contains("CURVE") || json.contains("LINE"))
    }
}
