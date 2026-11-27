package com.kodraliu.localrock.shared.vacuum.camera

/** Monotonic-time watchdog. A connection or a single decoded frame is not proof of live video. */
internal class CameraFrameFreshness(private val timeoutMs: Long = 4_000L) {
    private var previousCount: Long? = null
    private var lastAdvanceMs: Long? = null

    fun observe(count: Long, nowMs: Long) {
        val previous = previousCount
        if (count <= 0 || (previous != null && count < previous)) {
            lastAdvanceMs = null
        } else if (previous != null && previous > 0 && count > previous) {
            lastAdvanceMs = nowMs
        }
        previousCount = count
    }

    fun isFresh(nowMs: Long): Boolean {
        val age = lastAdvanceMs?.let { nowMs - it } ?: return false
        return age in 0 until timeoutMs
    }

    fun reset() {
        previousCount = null
        lastAdvanceMs = null
    }
}
