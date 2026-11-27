package com.kodraliu.localrock.ui.vacuum

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.*
import com.kodraliu.localrock.shared.vacuum.camera.CameraMotionController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal actual val cameraGamepadSupported: Boolean = true

private fun connectedPads() = InputDevice.getDeviceIds().map { InputDevice.getDevice(it) }.filterNotNull()
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
    private val state = CameraGamepadState(
        busy = { motionController.busy.value },
        move = { input, start -> motionController.pilotInput(input.throttle, input.steering, start) },
        release = motionController::release,
    )

    private fun publishStatus() { status = state.status }

    private fun select(id: Int): Boolean {
        if (deviceId != null && deviceId != id) return false
        if (deviceId == null && connectedPads().none { it.id == id }) return false
        deviceId = id
        return true
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
        val input = cameraPadInput(layout, x, y, left, right)
        if (input == null) state.disarm("Invalid controller axes · centre controls and press A")
        else state.input(input, event.eventTime, SystemClock.uptimeMillis())
        publishStatus()
        return true
    }

    fun key(event: KeyEvent): Boolean {
        val source = event.source
        if (source and InputDevice.SOURCE_GAMEPAD != InputDevice.SOURCE_GAMEPAD &&
            source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        if (event.keyCode != KeyEvent.KEYCODE_BUTTON_A && event.keyCode != KeyEvent.KEYCODE_BUTTON_B) return false
        if (!select(event.deviceId)) return false
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return true
        val age = SystemClock.uptimeMillis() - event.eventTime
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_B || age !in 0 until 1_500L) state.disarm()
        else state.toggle()
        publishStatus()
        return true
    }

    fun tick() {
        val id = deviceId ?: return
        if (InputDevice.getDevice(id) == null) {
            reset("Controller disconnected")
            return
        }
        state.tick(SystemClock.uptimeMillis())
        publishStatus()
    }

    fun reset(message: String) {
        state.disarm(message)
        deviceId = null
        publishStatus()
    }
}
