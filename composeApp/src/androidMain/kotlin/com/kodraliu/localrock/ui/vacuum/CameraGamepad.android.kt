package com.kodraliu.localrock.ui.vacuum

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.*
import com.kodraliu.localrock.shared.vacuum.camera.CameraMotionController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

internal actual val cameraGamepadSupported: Boolean = true

private fun connectedPads() = InputDevice.getDeviceIds().mapNotNull { InputDevice.getDevice(it) }
    .filter { !it.isVirtual && it.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK &&
        it.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD }

@Composable
internal actual fun cameraConnectedGamepad(): String? {
    var name by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (isActive) {
            val pads = connectedPads()
            name = if (pads.size == 1) pads.single().name else if (pads.size > 1) "Multiple controllers" else null
            delay(750)
        }
    }
    return name
}

/** Only installed while the user explicitly selects Controller in Pilot mode. */
internal object CameraGamepadBridge {
    var session: AndroidCameraGamepad? = null
    fun motion(event: MotionEvent) = session?.motion(event) == true
    fun key(event: KeyEvent) = session?.key(event) == true
    fun reset() { session?.reset("Focus lost · release controls") }
}

@Composable
internal actual fun CameraGamepadSession(motion: CameraMotionController, enabled: Boolean, layout: CameraPadLayout): String {
    val session = remember(motion, layout) { AndroidCameraGamepad(motion, layout) }
    DisposableEffect(session, enabled) {
        if (enabled) {
            session.reset("Move and centre controls to initialise")
            CameraGamepadBridge.session = session
        }
        onDispose {
            if (CameraGamepadBridge.session === session) {
                CameraGamepadBridge.session = null
                session.reset("Controller off")
            }
        }
    }
    LaunchedEffect(session, enabled) {
        if (enabled) while (isActive) {
            session.tick()
            delay(100)
        }
    }
    return session.status
}

internal class AndroidCameraGamepad(private val motionController: CameraMotionController,
    private val layout: CameraPadLayout) {
    var status by mutableStateOf("Move and centre controls to initialise")
        private set
    private var deviceId: Int? = null
    private var neutralSeen = false
    private var armed = false
    private var running = false
    private var lastInput = 0L
    private var steering = 0.0
    private var throttle = 0.0
    private var conflict = false
    private val staleMs = 1500L

    private fun select(id: Int): Boolean {
        if (deviceId != null && deviceId != id) return false
        if (deviceId == null && connectedPads().none { it.id == id }) return false
        deviceId = id
        return true
    }

    private fun deadzone(value: Double, dead: Double): Double {
        if (abs(value) <= dead) return 0.0
        val magnitude = (abs(value) - dead) / (1.0 - dead)
        return if (value < 0.0) -magnitude else magnitude
    }

    private fun trigger(event: MotionEvent, primary: Int, fallback: Int): Double? {
        val device = event.device ?: return null
        val range = device.getMotionRange(primary, event.source) ?: device.getMotionRange(primary)
            ?: device.getMotionRange(fallback, event.source) ?: device.getMotionRange(fallback)
            ?: return null
        if (range.range <= 0f) return null
        val raw = event.getAxisValue(range.axis)
        if (!raw.isFinite()) return null
        return ((raw - range.min) / range.range).toDouble().coerceIn(0.0, 1.0)
    }

    fun motion(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK ||
            event.actionMasked != MotionEvent.ACTION_MOVE || !select(event.deviceId)) return false
        val x = event.getAxisValue(MotionEvent.AXIS_X).toDouble()
        val y = event.getAxisValue(MotionEvent.AXIS_Y).toDouble()
        val left = if (layout == CameraPadLayout.RACING) trigger(event, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE) else 0.0
        val right = if (layout == CameraPadLayout.RACING) trigger(event, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS) else 0.0
        if (!x.isFinite() || !y.isFinite() || left == null || right == null ||
            event.device?.getMotionRange(MotionEvent.AXIS_X) == null ||
            (layout == CameraPadLayout.JOYSTICK && event.device?.getMotionRange(MotionEvent.AXIS_Y) == null)) {
            reset("Unsupported controller axes · release controls")
            return true
        }
        lastInput = SystemClock.uptimeMillis()
        steering = -deadzone(x.coerceIn(-1.0, 1.0), .15)
        conflict = layout == CameraPadLayout.RACING && left > .08 && right > .08
        throttle = if (conflict) 0.0 else if (layout == CameraPadLayout.JOYSTICK)
            -deadzone(y.coerceIn(-1.0, 1.0), .15) else deadzone(right, .08) - deadzone(left, .08)
        val neutral = !conflict && steering == 0.0 && throttle == 0.0
        if (conflict) {
            stop("Release both triggers · press A to re-enable")
            neutralSeen = false
            return true
        }
        if (neutral) {
            neutralSeen = true
            if (running) motionController.release()
            running = false
            status = if (armed) "Controller ready · move to drive" else "Press A to enable driving"
        } else if (armed) {
            // Never queue a fresh movement while a previous stop is still being confirmed.
            if (!running && motionController.busy.value) {
                stop("Previous drive stopping · centre controls and press A")
                neutralSeen = false
                return true
            }
            val accepted = motionController.pilotInput(throttle, steering, start = !running)
            if (!accepted) {
                stop("Drive unavailable · centre controls and press A")
                neutralSeen = false
            } else {
                running = true
                status = "Controller driving · release controls to stop"
            }
        } else {
            neutralSeen = false
            status = "Centre controls, then press A"
        }
        return true
    }

    fun key(event: KeyEvent): Boolean {
        val source = event.source
        if (source and InputDevice.SOURCE_GAMEPAD != InputDevice.SOURCE_GAMEPAD &&
            source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        if (event.keyCode != KeyEvent.KEYCODE_BUTTON_A && event.keyCode != KeyEvent.KEYCODE_BUTTON_B) return false
        if (!select(event.deviceId)) return false
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return true
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B || armed) {
            stop("Controller disabled · press A to enable")
            return true
        }
        if (neutralSeen && !conflict && steering == 0.0 && throttle == 0.0 &&
            !motionController.busy.value) {
            armed = true
            status = "Controller ready · move to drive"
        } else status = "Centre controls first, then press A"
        return true
    }

    fun tick() {
        val id = deviceId ?: return
        if (InputDevice.getDevice(id) == null) {
            reset("Controller disconnected")
            return
        }
        if (running && SystemClock.uptimeMillis() - lastInput > staleMs) {
            stop("Input stale · centre controls and press A")
            neutralSeen = false
        }
        if (running && !motionController.busy.value) {
            stop("Drive ended · centre controls and press A")
            neutralSeen = false
        }
    }

    private fun stop(message: String) {
        if (running || armed) motionController.release()
        running = false
        armed = false
        status = message
    }

    fun reset(message: String) {
        stop(message)
        deviceId = null
        neutralSeen = false
        lastInput = 0L
        steering = 0.0
        throttle = 0.0
        conflict = false
    }
}
