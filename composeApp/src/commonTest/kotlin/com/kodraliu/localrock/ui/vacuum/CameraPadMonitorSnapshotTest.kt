package com.kodraliu.localrock.ui.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraPadMonitorSnapshotTest {
    @Test fun simultaneousButtonsAndRelease() {
        val both=CameraPadMonitorSnapshot(available=true)
            .withButton(CameraPadButton.A,true).withButton(CameraPadButton.B,true)
        assertEquals(setOf(CameraPadButton.A,CameraPadButton.B),both.pressed)
        assertEquals(setOf(CameraPadButton.B),both.withButton(CameraPadButton.A,false).pressed)
    }

    @Test fun hatAndKeySourcesAreIndependent() {
        val s=CameraPadMonitorSnapshot(hat=setOf(CameraPadButton.UP))
            .withButton(CameraPadButton.UP,true).withButton(CameraPadButton.UP,false)
        assertEquals(setOf(CameraPadButton.UP),s.pressed)
        assertTrue(s.copy(hat=emptySet()).pressed.isEmpty())
    }

    @Test fun monitorShowsActualDrivingDeadzoneAndConflictRules() {
        val neutral=CameraPadMonitorSnapshot(leftX=.12,leftY=.10,rightTrigger=.07)
        assertTrue(neutral.effective(CameraPadLayout.RACING)!!.neutral)
        assertTrue(neutral.effective(CameraPadLayout.JOYSTICK)!!.neutral)
        val both=neutral.copy(leftTrigger=.5,rightTrigger=.5)
        assertTrue(both.effective(CameraPadLayout.RACING)!!.conflicting)
        assertFalse(both.effective(CameraPadLayout.JOYSTICK)!!.conflicting)
        assertEquals(1.0,neutral.copy(leftY=-1.0).effective(CameraPadLayout.JOYSTICK)!!.throttle)
    }

    @Test fun clearDropsAllPreviousInputAndDeviceIdentity() {
        val s=CameraPadMonitorSnapshot(available=true,deviceName="Test controller",leftX=.5,rightTrigger=.8,
            buttons=setOf(CameraPadButton.A),hat=setOf(CameraPadButton.LEFT))
        assertEquals(CameraPadMonitorSnapshot(available=true),s.cleared())
    }
}
