package com.kodraliu.localrock.ui.vacuum

import androidx.compose.runtime.Composable
import com.kodraliu.localrock.shared.vacuum.camera.CameraMotionController

internal expect val cameraGamepadSupported: Boolean

@Composable
internal expect fun CameraGamepadSession(motion: CameraMotionController, enabled: Boolean): String
