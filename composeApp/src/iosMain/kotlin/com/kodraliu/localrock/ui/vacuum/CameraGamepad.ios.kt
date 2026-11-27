package com.kodraliu.localrock.ui.vacuum

import androidx.compose.runtime.Composable
import com.kodraliu.localrock.shared.vacuum.camera.CameraMotionController

internal actual val cameraGamepadSupported: Boolean = false

@Composable
internal actual fun cameraConnectedGamepad(): String? = null

@Composable
internal actual fun CameraGamepadSession(motion: CameraMotionController, enabled: Boolean, layout: CameraPadLayout, enableButton: CameraPadEnable): String = "Controller unavailable"
