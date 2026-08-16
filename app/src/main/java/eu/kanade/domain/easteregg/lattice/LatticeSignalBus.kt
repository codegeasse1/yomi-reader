package eu.kanade.domain.easteregg.lattice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Шина событий пасхалки: домен → UI и домен → App-хук наград. */
object LatticeSignalBus {

    private val _latchEvents = MutableStateFlow(0L)
    val latchEvents: StateFlow<Long> = _latchEvents

    private val _breach = MutableStateFlow(false)
    val breach: StateFlow<Boolean> = _breach

    private val _synthesis = MutableStateFlow(false)
    val synthesis: StateFlow<Boolean> = _synthesis

    private val _whisper = MutableStateFlow<String?>(null)
    val whisper: StateFlow<String?> = _whisper

    /** Выставляется в App.kt; вызывается ровно один раз при успешном синтезе. */
    @Volatile
    var onUnlocked: ((LatticePayload) -> Unit)? = null

    fun emitLatch() {
        _latchEvents.value = System.currentTimeMillis()
    }

    fun emitBreach() {
        _breach.value = true
    }

    fun consumeBreach() {
        _breach.value = false
    }

    fun emitSynthesis() {
        _synthesis.value = true
    }

    fun consumeSynthesis() {
        _synthesis.value = false
    }

    fun emitWhisper(line: String) {
        _whisper.value = line
    }

    fun consumeWhisper() {
        _whisper.value = null
    }
}
