package com.kodraliu.localrock.shared.vacuum.camera

import kotlin.math.abs

/** Requested robot command values, not measured chassis speeds. */
internal data class CameraDriveVector(val velocity: Double, val omega: Double)

/** Keep the experimental profile separate from the hardware-tested OEM controls. */
internal object SarosManualDriveProfile {
    const val MAX_FORWARD_MPS = 0.30
    const val MAX_REVERSE_MPS = 0.12
    const val MAX_TURN_RAD_PER_SECOND = 0.40
    const val DIRECTION_PAD_LIMIT_MPS = 0.08
    const val CORNER_SLOWDOWN_THRESHOLD_MPS = 0.12
    const val CORNER_SLOWDOWN_FRACTION = 0.40
    const val COMMAND_DURATION_MS = 500

    val initialStates = setOf(3, 8, 100)
    val activeStates = setOf(7)

    fun analogue(throttle: Double, steering: Double): CameraDriveVector? {
        if (!throttle.isFinite() || !steering.isFinite() ||
            throttle !in -1.0..1.0 || steering !in -1.0..1.0
        ) return null

        val limit = if (throttle < 0.0) MAX_REVERSE_MPS else MAX_FORWARD_MPS
        var velocity = throttle * limit
        if (abs(velocity) > CORNER_SLOWDOWN_THRESHOLD_MPS) {
            velocity *= 1.0 - CORNER_SLOWDOWN_FRACTION * abs(steering)
        }
        return CameraDriveVector(velocity, steering * MAX_TURN_RAD_PER_SECOND)
    }

    fun directional(velocity: Double, omega: Double) = CameraDriveVector(
        velocity.coerceIn(-DIRECTION_PAD_LIMIT_MPS, DIRECTION_PAD_LIMIT_MPS),
        omega.coerceIn(-MAX_TURN_RAD_PER_SECOND, MAX_TURN_RAD_PER_SECOND),
    )
}
