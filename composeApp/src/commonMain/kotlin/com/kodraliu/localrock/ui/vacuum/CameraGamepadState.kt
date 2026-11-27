package com.kodraliu.localrock.ui.vacuum

import kotlin.math.abs

internal data class CameraPadInput(val throttle: Double, val steering: Double, val conflicting: Boolean = false) {
    val valid: Boolean get() = throttle.isFinite() && steering.isFinite() &&
        throttle in -1.0..1.0 && steering in -1.0..1.0
    val neutral: Boolean get() = valid && !conflicting && throttle == 0.0 && steering == 0.0
}

/** Platform-independent input normalization; unsupported axes are rejected by the platform adapter. */
internal fun cameraPadInput(layout: CameraPadLayout, x: Double, y: Double, left: Double, right: Double): CameraPadInput? {
    if (listOf(x, y, left, right).any { !it.isFinite() } ||
        x !in -1.0..1.0 || y !in -1.0..1.0 || left !in 0.0..1.0 || right !in 0.0..1.0) return null
    fun deadzone(value: Double, dead: Double): Double {
        if (abs(value) <= dead) return 0.0
        val magnitude = (abs(value) - dead) / (1.0 - dead)
        return if (value < 0.0) -magnitude else magnitude
    }
    val steering = -deadzone(x, .15) + 0.0
    if (layout == CameraPadLayout.JOYSTICK) return CameraPadInput(-deadzone(y, .15), steering)
    if (left > .08 && right > .08) return CameraPadInput(0.0, 0.0, conflicting = true)
    return CameraPadInput(deadzone(right, .08) - deadzone(left, .08), steering)
}

// Android reports axis changes, not a guaranteed periodic heartbeat for a steady stick.
// A bounded silence lease permits ordinary holds but never substitutes for a fresh event.
internal const val CAMERA_PAD_INPUT_LEASE_MS = 5_000L
internal const val CAMERA_PAD_EVENT_MAX_AGE_MS = 1_500L

internal enum class CameraPadState {
    WAIT_NEUTRAL, DISARMED, ARMED_IDLE, DRIVING, STOPPING
}

/**
 * All calls are serialized on the UI thread. The platform adapter owns device selection
 * and the motion controller owns robot RPCs. No transition queues future movement.
 */
internal class CameraGamepadState(
    private val busy: () -> Boolean,
    private val move: (CameraPadInput, Boolean) -> Boolean,
    private val release: () -> Unit,
    private val staleMs: Long = CAMERA_PAD_INPUT_LEASE_MS,
    private val motionFailed: () -> Boolean = { false },
) {
    var state = CameraPadState.WAIT_NEUTRAL
        private set
    var status = "Move and centre controls to initialise"
        private set
    private var lastInputMs: Long? = null
    private var lastInputNeutral = false

    fun input(input: CameraPadInput, eventMs: Long, nowMs: Long) {
        if (!input.valid || input.conflicting || nowMs - eventMs !in 0L until CAMERA_PAD_EVENT_MAX_AGE_MS ||
            (lastInputMs?.let { eventMs < it } == true)) {
            disarm(if (input.conflicting) "Release both triggers · press A to re-enable" else "Invalid or stale input · centre controls and press A")
            return
        }
        lastInputMs = eventMs
        lastInputNeutral = input.neutral
        when (state) {
            CameraPadState.WAIT_NEUTRAL, CameraPadState.DISARMED -> {
                if (input.neutral) set(CameraPadState.DISARMED, "Press A to enable driving")
                else set(CameraPadState.WAIT_NEUTRAL, "Centre controls, then press A")
            }
            CameraPadState.ARMED_IDLE -> {
                if (!input.neutral) {
                    if (busy() || !move(input, true)) disarm("Drive unavailable · centre controls and press A")
                    else set(CameraPadState.DRIVING, "Controller driving · release controls to stop")
                }
            }
            CameraPadState.DRIVING -> {
                if (input.neutral) {
                    release()
                    set(CameraPadState.STOPPING, "Stopping…")
                } else if (!move(input, false)) disarm("Drive unavailable · centre controls and press A")
            }
            CameraPadState.STOPPING -> {
                // A new deflection during cleanup must not restart motion when cleanup finishes.
                if (!input.neutral) disarm("Previous drive stopping · centre controls and press A")
            }
        }
    }

    fun toggle() {
        when (state) {
            CameraPadState.DISARMED -> {
                if (lastInputNeutral && !busy()) set(CameraPadState.ARMED_IDLE, "Controller ready · move to drive")
                else disarm("Centre controls and wait for stop, then press A")
            }
            CameraPadState.WAIT_NEUTRAL -> status = "Centre controls first, then press A"
            else -> disarm("Controller disabled · centre controls and press A")
        }
    }

    fun tick(nowMs: Long) {
        if ((state == CameraPadState.DRIVING || state == CameraPadState.STOPPING) && motionFailed()) {
            disarm("Drive not confirmed · centre controls and press A")
            return
        }
        when (state) {
            CameraPadState.DRIVING -> {
                val age = lastInputMs?.let { nowMs - it }
                if (age == null || age !in 0L until staleMs) disarm("Input unchanged too long · centre controls and press A")
                else if (!busy()) disarm("Drive ended · centre controls and press A")
            }
            CameraPadState.STOPPING -> if (!busy()) {
                if (lastInputNeutral) set(CameraPadState.ARMED_IDLE, "Controller ready · move to drive")
                else disarm("Centre controls and press A")
            }
            else -> Unit
        }
    }

    fun disarm(message: String = "Controller disabled · centre controls and press A") {
        if (state == CameraPadState.DRIVING || state == CameraPadState.STOPPING) release()
        lastInputMs = null
        lastInputNeutral = false
        set(CameraPadState.WAIT_NEUTRAL, message)
    }

    private fun set(next: CameraPadState, message: String) {
        state = next
        status = message
    }
}
