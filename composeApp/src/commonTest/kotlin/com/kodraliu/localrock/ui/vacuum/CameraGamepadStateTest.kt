package com.kodraliu.localrock.ui.vacuum

import kotlin.test.*

class CameraGamepadStateTest {
    private class Rig {
        var busy = false
        var accepted = true
        var releases = 0
        val moves = mutableListOf<Pair<CameraPadInput, Boolean>>()
        val machine = CameraGamepadState(
            busy = { busy },
            move = { input, start ->
                moves += input to start
                if (accepted) busy = true
                accepted
            },
            release = { releases++ },
        )
        fun input(throttle: Double, steering: Double = 0.0, time: Long = 0) =
            machine.input(CameraPadInput(throttle, steering), time, time)
        fun arm() { input(0.0); machine.toggle() }
    }

    @Test fun explicitEnableAndNeutralAreRequired() {
        val r = Rig()
        r.input(1.0)
        r.machine.toggle()
        assertTrue(r.moves.isEmpty())
        r.arm()
        assertEquals(CameraPadState.ARMED_IDLE, r.machine.state)
        r.input(.5)
        r.input(.7, .2)
        assertEquals(listOf(true, false), r.moves.map { it.second })
        assertEquals(CameraPadState.DRIVING, r.machine.state)
    }

    @Test fun neutralStopsAndNoNewDriveIsQueuedDuringCleanup() {
        val r = Rig()
        r.arm(); r.input(1.0); r.input(0.0)
        assertEquals(1, r.releases)
        assertEquals(CameraPadState.STOPPING, r.machine.state)
        r.input(1.0)
        r.busy = false
        r.machine.tick(100)
        assertEquals(CameraPadState.WAIT_NEUTRAL, r.machine.state)
        assertEquals(1, r.moves.size)
        r.arm(); r.input(.3)
        assertEquals(2, r.moves.size)
    }

    @Test fun ordinaryNeutralStopRearmsOnlyAfterCleanup() {
        val r = Rig()
        r.arm(); r.input(1.0); r.input(0.0)
        r.machine.tick(100)
        assertEquals(CameraPadState.STOPPING, r.machine.state)
        r.busy = false
        r.machine.tick(200)
        assertEquals(CameraPadState.ARMED_IDLE, r.machine.state)
        r.input(.5, time = 250)
        assertEquals(2, r.moves.size)
        assertTrue(r.moves.last().second)
    }

    @Test fun staleInputDisarmsAndDoesNotResumeAutomatically() {
        val r = Rig()
        r.arm(); r.input(1.0)
        r.machine.tick(1_499)
        assertEquals(CameraPadState.DRIVING, r.machine.state)
        r.machine.tick(1_500)
        assertEquals(CameraPadState.WAIT_NEUTRAL, r.machine.state)
        assertEquals(1, r.releases)
        r.busy = false
        r.input(0.0, time = 1_600)
        r.input(1.0, time = 1_700)
        assertEquals(1, r.moves.size)
    }

    @Test fun staleQueuedEventsAndConflictingTriggersFailClosed() {
        val r = Rig()
        r.arm(); r.input(.5)
        r.machine.input(CameraPadInput(1.0, 0.0), 10, 2_000)
        assertEquals(CameraPadState.WAIT_NEUTRAL, r.machine.state)
        r.busy = false
        r.arm(); r.input(.5)
        r.machine.input(CameraPadInput(0.0, 0.0, conflicting = true), 0, 0)
        assertEquals(CameraPadState.WAIT_NEUTRAL, r.machine.state)
        assertEquals(2, r.releases)
    }

    @Test fun focusDisconnectAndStopShareTheDisarmPath() {
        for (reason in listOf("Focus lost", "Controller disconnected", "B pressed")) {
            val r = Rig()
            r.arm(); r.input(1.0)
            r.machine.disarm(reason)
            assertEquals(1, r.releases)
            assertEquals(CameraPadState.WAIT_NEUTRAL, r.machine.state)
            r.machine.toggle()
            assertEquals(1, r.moves.size)
        }
    }

    @Test fun racingAndJoystickNormalization() {
        val racing = CameraPadLayout.RACING
        val joystick = CameraPadLayout.JOYSTICK
        assertEquals(CameraPadInput(1.0, 0.0), cameraPadInput(racing, 0.0, 0.0, 0.0, 1.0))
        assertEquals(-1.0, cameraPadInput(racing, 0.0, 0.0, 1.0, 0.0)?.throttle)
        assertTrue(assertNotNull(cameraPadInput(racing, 0.0, 0.0, .5, .5)).conflicting)
        assertEquals(1.0, cameraPadInput(joystick, 0.0, -1.0, 0.0, 0.0)?.throttle)
        assertEquals(-1.0, cameraPadInput(joystick, 1.0, 0.0, 0.0, 0.0)?.steering)
        assertTrue(assertNotNull(cameraPadInput(joystick, .1, .1, 0.0, 0.0)).neutral)
        assertNull(cameraPadInput(racing, Double.NaN, 0.0, 0.0, 0.0))
    }
}
