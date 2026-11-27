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
internal actual fun CameraGamepadSession(motion: CameraMotionController, enabled: Boolean, layout: CameraPadLayout, enableButton: CameraPadEnable): String {
    val session = remember(motion, layout, enableButton) { AndroidCameraGamepad(motion, layout, enableButton) }
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
    private val layout: CameraPadLayout, private val enableButton: CameraPadEnable) {
    private val holdLabel = if (enableButton == CameraPadEnable.A) "A" else "LB"
    private val holdCode = if (enableButton == CameraPadEnable.A) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_L1
    var status by mutableStateOf("Move and centre controls to initialise")
        private set
    private var deviceId: Int? = null
    private var neutralSeen = false
    private var armed = false
    private var running = false
    private var blocked = false
    private var aDown = false
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
        if (!aDown && !conflict && steering == 0.0 && throttle == 0.0) {
            neutralSeen = true
            blocked = false
            status = "Hold $holdLabel to drive"
        }
        if (conflict) { stop("Release both triggers"); blocked = true; neutralSeen = false; return true }
        if (armed && aDown && !blocked) {
            if (steering == 0.0 && throttle == 0.0) {
                if (running) motionController.release()
                running = false
                status = "$holdLabel held · ready"
            } else {
                val accepted = motionController.pilotInput(throttle, steering, start = !running)
                if (!accepted) {
                    stop("Drive unavailable · release $holdLabel")
                    blocked = true
                    neutralSeen = false
                } else {
                    running = true
                    status = "Controller driving · release $holdLabel to stop"
                }
            }
        }
        return true
    }

    fun key(event: KeyEvent): Boolean {
        val source = event.source
        if (source and InputDevice.SOURCE_GAMEPAD != InputDevice.SOURCE_GAMEPAD &&
            source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        if (event.keyCode != holdCode && event.keyCode != KeyEvent.KEYCODE_BUTTON_B) return false
        if (!select(event.deviceId)) return false
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                stop("Stopped · release $holdLabel and controls")
                blocked = true
                neutralSeen = false
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_UP) {
            aDown = false
            stop("Release controls, then hold $holdLabel")
            if (!conflict && steering == 0.0 && throttle == 0.0 &&
                SystemClock.uptimeMillis() - lastInput <= staleMs) {
                neutralSeen = true
                blocked = false
                status = "Hold $holdLabel to drive"
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            aDown = true
            if (!blocked && neutralSeen && !conflict && steering == 0.0 && throttle == 0.0 &&
                SystemClock.uptimeMillis() - lastInput <= staleMs) {
                armed = true
                status = "$holdLabel held · ready"
            } else status = "Release $holdLabel and centre controls first"
        }
        return true
    }

    fun tick() {
        val id = deviceId ?: return
        if (InputDevice.getDevice(id) == null) {
            reset("Controller disconnected")
            return
        }
        if (armed && SystemClock.uptimeMillis() - lastInput > staleMs) {
            stop("Input stale · release $holdLabel and re-arm")
            blocked = true
            neutralSeen = false
        }
        if (running && !motionController.busy.value) {
            stop("Drive ended · release $holdLabel")
            blocked = true
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
        aDown = false
        blocked = false
        lastInput = 0L
        steering = 0.0
        throttle = 0.0
        conflict = false
    }
}
