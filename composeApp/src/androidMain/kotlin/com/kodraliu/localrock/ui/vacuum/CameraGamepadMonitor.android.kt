package com.kodraliu.localrock.ui.vacuum

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Compose AlertDialog has its own Android window; its gamepad events need not reach
 * MainActivity. Capture them in that window only, never via the driving bridge.
 */
@Composable
internal actual fun CameraGamepadMonitor(): CameraPadMonitorSnapshot {
    val view = LocalView.current
    val context = LocalContext.current
    val window = remember(view) {
        generateSequence(view.parent) { it.parent }
            .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
    }
    val manager = remember(context) { context.getSystemService(Context.INPUT_SERVICE) as InputManager }
    val monitor = remember(window) { AndroidPadMonitor() }
    DisposableEffect(window, manager, monitor) {
        val previous = window?.callback
        if (window != null && previous != null) {
            val callback = object : Window.Callback by previous {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                    monitor.key(event) || previous.dispatchKeyEvent(event)
                override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
                    monitor.motion(event) || previous.dispatchGenericMotionEvent(event)
                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    if (!hasFocus) monitor.reset()
                    previous.onWindowFocusChanged(hasFocus)
                }
            }
            val listener = object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) = Unit
                override fun onInputDeviceChanged(deviceId: Int) = monitor.deviceChanged(deviceId)
                override fun onInputDeviceRemoved(deviceId: Int) = monitor.deviceChanged(deviceId)
            }
            window.callback = callback
            monitor.setAvailable(true)
            manager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
            onDispose {
                manager.unregisterInputDeviceListener(listener)
                if (window.callback === callback) window.callback = previous
                monitor.setAvailable(false)
            }
        } else onDispose { monitor.setAvailable(false) }
    }
    return monitor.snapshot
}

private class AndroidPadMonitor {
    var snapshot by mutableStateOf(CameraPadMonitorSnapshot())
        private set
    private var deviceId: Int? = null

    fun setAvailable(value: Boolean) {
        reset()
        snapshot = snapshot.copy(available = value)
    }

    fun reset() {
        deviceId = null
        snapshot = snapshot.cleared()
    }

    fun deviceChanged(id: Int) {
        if (deviceId == id) reset()
    }

    private fun select(id: Int): Boolean {
        val device = InputDevice.getDevice(id) ?: return false
        if (device.isVirtual || device.sources and InputDevice.SOURCE_GAMEPAD != InputDevice.SOURCE_GAMEPAD)
            return false
        if (deviceId != null && deviceId != id) return false
        deviceId = id
        if (snapshot.deviceName != device.name) snapshot = snapshot.copy(deviceName = device.name)
        return true
    }

    private fun fresh(eventTime: Long) =
        SystemClock.uptimeMillis() - eventTime in 0L until CAMERA_PAD_EVENT_MAX_AGE_MS

    private fun axis(event: MotionEvent, vararg candidates: Int, trigger: Boolean = false): Double {
        val device = event.device ?: return 0.0
        val range = candidates.asSequence().firstNotNullOfOrNull {
            device.getMotionRange(it, event.source) ?: device.getMotionRange(it)
        } ?: return 0.0
        val raw = event.getAxisValue(range.axis).toDouble()
        if (!raw.isFinite() || range.range <= 0f) return 0.0
        // Match the driving adapter's trigger normalization; stick axes are normally -1..1.
        return if (trigger) ((raw - range.min) / range.range).coerceIn(0.0, 1.0)
        else raw.coerceIn(-1.0, 1.0)
    }

    fun motion(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        // Consume controller events in the test dialog, including non-selected controllers.
        if (!select(event.deviceId)) return true
        if (!fresh(event.eventTime) || event.actionMasked != MotionEvent.ACTION_MOVE) {
            reset()
            return true
        }
        val hx = axis(event, MotionEvent.AXIS_HAT_X)
        val hy = axis(event, MotionEvent.AXIS_HAT_Y)
        snapshot = snapshot.copy(
            leftX = axis(event, MotionEvent.AXIS_X),
            leftY = axis(event, MotionEvent.AXIS_Y),
            rightX = axis(event, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX),
            rightY = axis(event, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY),
            leftTrigger = axis(event, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE, trigger = true),
            rightTrigger = axis(event, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS, trigger = true),
            hat = buildSet {
                if (hx < -.5) add(CameraPadButton.LEFT)
                if (hx > .5) add(CameraPadButton.RIGHT)
                if (hy < -.5) add(CameraPadButton.UP)
                if (hy > .5) add(CameraPadButton.DOWN)
            }
        )
        return true
    }

    fun key(event: KeyEvent): Boolean {
        val source = event.source
        val gamepad = source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        if (!gamepad || event.device?.let {
                it.isVirtual || it.sources and InputDevice.SOURCE_GAMEPAD != InputDevice.SOURCE_GAMEPAD
            } != false) return false
        if (!select(event.deviceId)) return true
        if (!fresh(event.eventTime) || event.isCanceled) { reset(); return true }
        val button = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> CameraPadButton.A
            KeyEvent.KEYCODE_BUTTON_B -> CameraPadButton.B
            KeyEvent.KEYCODE_BUTTON_X -> CameraPadButton.X
            KeyEvent.KEYCODE_BUTTON_Y -> CameraPadButton.Y
            KeyEvent.KEYCODE_BUTTON_L1 -> CameraPadButton.LB
            KeyEvent.KEYCODE_BUTTON_R1 -> CameraPadButton.RB
            KeyEvent.KEYCODE_BUTTON_L2 -> CameraPadButton.LT
            KeyEvent.KEYCODE_BUTTON_R2 -> CameraPadButton.RT
            KeyEvent.KEYCODE_BUTTON_THUMBL -> CameraPadButton.LEFT_STICK
            KeyEvent.KEYCODE_BUTTON_THUMBR -> CameraPadButton.RIGHT_STICK
            KeyEvent.KEYCODE_BUTTON_SELECT -> CameraPadButton.BACK
            KeyEvent.KEYCODE_BUTTON_START -> CameraPadButton.START
            KeyEvent.KEYCODE_DPAD_UP -> CameraPadButton.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> CameraPadButton.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> CameraPadButton.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> CameraPadButton.RIGHT
            else -> null
        }
        if (button != null && (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP))
            snapshot = snapshot.withButton(button, event.action == KeyEvent.ACTION_DOWN)
        return true
    }
}
