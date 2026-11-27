package com.kodraliu.localrock.shared.vacuum.camera

import kotlin.test.*

class CameraFrameFreshnessTest {
    @Test fun aFrozenFirstFrameNeverEnablesDriving() {
        val watchdog = CameraFrameFreshness()
        watchdog.observe(1, 0)
        watchdog.observe(1, 2_000)
        assertFalse(watchdog.isFresh(2_000))
    }

    @Test fun decodedFrameProgressExpiresEvenWithoutMoreSamples() {
        val watchdog = CameraFrameFreshness()
        watchdog.observe(1, 0)
        watchdog.observe(30, 1_000)
        assertTrue(watchdog.isFresh(4_999))
        assertFalse(watchdog.isFresh(5_000))
        watchdog.observe(30, 6_000)
        assertFalse(watchdog.isFresh(6_000))
        watchdog.observe(31, 6_100)
        assertTrue(watchdog.isFresh(6_100))
    }

    @Test fun missingStatsCounterResetAndNewSessionFailClosed() {
        val watchdog = CameraFrameFreshness()
        watchdog.observe(1, 0)
        watchdog.observe(20, 1_000)
        watchdog.observe(0, 2_000)
        assertFalse(watchdog.isFresh(2_000))
        watchdog.observe(1, 3_000)
        assertFalse(watchdog.isFresh(3_000))
        watchdog.observe(2, 4_000)
        assertTrue(watchdog.isFresh(4_000))
        watchdog.observe(1, 4_100)
        assertFalse(watchdog.isFresh(4_100))
        watchdog.reset()
        assertFalse(watchdog.isFresh(4_200))
    }
}
