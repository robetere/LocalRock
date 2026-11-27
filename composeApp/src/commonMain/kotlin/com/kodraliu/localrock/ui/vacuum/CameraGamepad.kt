package com.kodraliu.localrock.ui.vacuum

import androidx.compose.runtime.Composable
import com.kodraliu.localrock.shared.vacuum.camera.CameraMotionController

internal enum class CameraPadLayout { JOYSTICK, RACING }
internal expect val cameraGamepadSupported: Boolean

@Composable
internal expect fun cameraConnectedGamepad(): String?

@Composable
internal expect fun CameraGamepadSession(motion: CameraMotionController, enabled: Boolean, layout: CameraPadLayout): String

/** Read-only controller preview, scoped to the settings dialog. */
@Composable
internal expect fun CameraGamepadMonitor(): CameraPadMonitorSnapshot
