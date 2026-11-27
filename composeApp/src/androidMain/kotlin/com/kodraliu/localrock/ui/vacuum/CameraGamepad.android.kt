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

/** Only installed while the user explicitly selects Controller in Pilot mode. */
internal object CameraGamepadBridge {
    var session: AndroidCameraGamepad? = null
    fun motion(event: MotionEvent) = session?.motion(event) == true
    fun key(event: KeyEvent) = session?.key(event) == true
    fun reset() { session?.reset("Focus lost · release A") }
}

@Composable
internal actual fun CameraGamepadSession(motion: CameraMotionController, enabled: Boolean): String {
    val session = remember(motion) { AndroidCameraGamepad(motion) }
    DisposableEffect(session, enabled) {
        if (enabled) {
            session.reset("Move stick or triggers, then hold A")
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

internal class AndroidCameraGamepad(private val motionController: CameraMotionController) {
    var status by mutableStateOf("Move stick or triggers, then hold A")
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
        val left = trigger(event, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE)
        val right = trigger(event, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS)
        if (!x.isFinite() || left == null || right == null) {
            reset("Unsupported trigger mapping · release A")
            return true
        }
        lastInput = SystemClock.uptimeMillis()
        steering = -deadzone(x.coerceIn(-1.0, 1.0), .15)
        conflict = left > .08 && right > .08
        throttle = if (conflict) 0.0 else deadzone(right, .08) - deadzone(left, .08)
        if (!aDown && !conflict && steering == 0.0 && throttle == 0.0) {
            neutralSeen = true
            blocked = false
            status = "Hold A · RT/LT drive · stick steers"
        }
        if (conflict) { stop("Release both triggers"); blocked = true; neutralSeen = false; return true }
        if (armed && aDown && !blocked) {
            if (steering == 0.0 && throttle == 0.0) {
                if (running) motionController.release()
                running = false
                status = "A held · ready"
            } else {
                val accepted = motionController.pilotInput(throttle, steering, start = !running)
                if (!accepted) {
                    stop("Drive unavailable · release A")
                    blocked = true
                    neutralSeen = false
                } else {
                    running = true
                    status = "Controller driving · release A to stop"
                }
            }
        }
        return true
    }

    fun key(event: KeyEvent): Boolean {
        val source = event.source
        if (source and InputDevice.SOURCE_GAMEPAD != InputDevice.SOURCE_GAMEPAD &&
            source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        if (event.keyCode != KeyEvent.KEYCODE_BUTTON_A && event.keyCode != KeyEvent.KEYCODE_BUTTON_B) return false
        if (!select(event.deviceId)) return false
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                stop("Stopped · release A and controls")
                blocked = true
                neutralSeen = false
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_UP) {
            aDown = false
            stop("Release controls, then hold A")
            if (!conflict && steering == 0.0 && throttle == 0.0 &&
                SystemClock.uptimeMillis() - lastInput <= staleMs) {
                neutralSeen = true
                blocked = false
                status = "Hold A · RT/LT drive · stick steers"
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            aDown = true
            if (!blocked && neutralSeen && !conflict && steering == 0.0 && throttle == 0.0 &&
                SystemClock.uptimeMillis() - lastInput <= staleMs) {
                armed = true
                status = "A held · ready"
            } else status = "Release A and centre controls first"
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
            stop("Input stale · release A and re-arm")
            blocked = true
            neutralSeen = false
        }
        if (running && !motionController.busy.value) {
            stop("Drive ended · release A")
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
