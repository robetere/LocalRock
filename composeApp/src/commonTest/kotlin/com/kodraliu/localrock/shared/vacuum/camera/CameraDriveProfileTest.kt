package com.kodraliu.localrock.shared.vacuum.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CameraDriveProfileTest {
    @Test
    fun analogueInputUsesTheEstablishedSarosEnvelope() {
        fun check(throttle: Double, steering: Double, velocity: Double, omega: Double) {
            val vector = assertNotNull(SarosManualDriveProfile.analogue(throttle, steering))
            assertEquals(velocity, vector.velocity, 1e-9)
            assertEquals(omega, vector.omega, 1e-9)
        }
        check(1.0, 0.0, 0.30, 0.0)
        check(0.5, 0.0, 0.15, 0.0)
        check(-1.0, 0.0, -0.12, 0.0)
        check(1.0, 1.0, 0.18, 0.40)
        check(1.0, -1.0, 0.18, -0.40)
        check(0.0, -0.5, 0.0, -0.20)
        check(0.0, 0.0, 0.0, 0.0)
    }

    @Test
    fun invalidAxesAreRejectedRatherThanClamped() {
        for (axis in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.01, 1.01)) {
            assertNull(SarosManualDriveProfile.analogue(axis, 0.0))
            assertNull(SarosManualDriveProfile.analogue(0.0, axis))
        }
    }

    @Test
    fun directionalControlRetainsItsConservativeLimit() {
        val vector = SarosManualDriveProfile.directional(0.20, 1.05)
        assertEquals(0.08, vector.velocity, 1e-9)
        assertEquals(0.40, vector.omega, 1e-9)
    }
}
