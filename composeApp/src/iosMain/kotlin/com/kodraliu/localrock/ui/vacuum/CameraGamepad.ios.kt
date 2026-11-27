package com.kodraliu.localrock.ui.vacuum

import androidx.compose.runtime.Composable
import com.kodraliu.localrock.shared.vacuum.camera.CameraMotionController

internal actual val cameraGamepadSupported: Boolean = false

@Composable
internal actual fun CameraGamepadSession(motion: CameraMotionController, enabled: Boolean): String = "Controller unavailable"
