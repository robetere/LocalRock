package com.kodraliu.localrock.ui.vacuum

import androidx.compose.runtime.Composable

private object UnsupportedCameraBiometrics : CameraBiometricCredentials {
    override val available = false
    override val saved = false
    override suspend fun save(credential: String) = false
    override suspend fun unlock(): String? = null
    override fun forget() = Unit
}

@Composable
internal actual fun rememberCameraBiometricCredentials(robotScope: String): CameraBiometricCredentials =
    UnsupportedCameraBiometrics
