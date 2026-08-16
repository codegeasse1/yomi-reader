package eu.kanade.domain.easteregg.lattice

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class LatticeStage(
    val salt: String,
    val iv: String,
    val check: String,
    val data: String,
)

/**
 * PBKDF2-HMAC-SHA256 (180k) + AES-256-GCM (auth tag appended to ciphertext).
 * Key derivation uses manual HMAC (UTF-8 password bytes) to match Node
 * `crypto.pbkdf2Sync(Buffer.from(canonical,'utf8'), …)` on all Android versions
 * — same approach as AuroraVault (PBEKeySpec is not reliable for parity).
 */
object LatticeVault {

    private const val ITERATIONS = 180_000

    fun tryOpen(canonical: String, stage: LatticeStage): ByteArray? = try {
        val salt = Base64.getDecoder().decode(stage.salt)
        val key = pbkdf2(canonical.encodeToByteArray(), salt, ITERATIONS, 32)
        val expected = Base64.getDecoder().decode(stage.check)
        val actual = MessageDigest.getInstance("SHA-256").digest(key)
        if (!MessageDigest.isEqual(expected, actual)) {
            null
        } else {
            val iv = Base64.getDecoder().decode(stage.iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.doFinal(Base64.getDecoder().decode(stage.data))
        }
    } catch (_: Exception) {
        null
    }

    /** PBKDF2-HMAC-SHA256, bit-identical to Node crypto.pbkdf2Sync for UTF-8 passwords. */
    private fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, keyLen: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val blockCount = (keyLen + 31) / 32
        val out = ByteArray(blockCount * 32)
        for (block in 1..blockCount) {
            mac.reset()
            mac.update(salt)
            mac.update(
                byteArrayOf(
                    (block ushr 24).toByte(),
                    (block ushr 16).toByte(),
                    (block ushr 8).toByte(),
                    block.toByte(),
                ),
            )
            var u = mac.doFinal()
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, (block - 1) * 32, 32)
        }
        return out.copyOf(keyLen)
    }
}
