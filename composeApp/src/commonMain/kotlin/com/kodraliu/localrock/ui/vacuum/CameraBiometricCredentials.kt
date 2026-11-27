package com.kodraliu.localrock.ui.vacuum

import androidx.compose.runtime.Composable

/** Optional local storage. The robot must still verify every recovered credential. */
internal interface CameraBiometricCredentials {
    val available: Boolean
    val saved: Boolean
    suspend fun save(credential: String): Boolean
    suspend fun unlock(): String?
    fun forget()
}

@Composable
internal expect fun rememberCameraBiometricCredentials(robotScope: String): CameraBiometricCredentials
