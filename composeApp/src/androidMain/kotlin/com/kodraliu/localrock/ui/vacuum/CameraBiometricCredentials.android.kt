package com.kodraliu.localrock.ui.vacuum

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

@Composable
internal actual fun rememberCameraBiometricCredentials(robotScope: String): CameraBiometricCredentials {
    val context = LocalContext.current
    return remember(context, robotScope) { AndroidCameraBiometricCredentials(context, robotScope) }
}

/**
 * Android 11+ only: a Class 3 biometric authorizes each individual AES-GCM operation.
 * No plaintext, reusable PIN hash, device-credential fallback or time-based key authorization.
 */
private class AndroidCameraBiometricCredentials(
    private val context: Context,
    robotScope: String,
) : CameraBiometricCredentials {
    private val alias = "localrock.camera.v1." + MessageDigest.getInstance("SHA-256")
        .digest(robotScope.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    private val prefs = context.getSharedPreferences("camera_biometric_credentials_v1", Context.MODE_PRIVATE)

    override val available: Boolean get() = Build.VERSION.SDK_INT >= 30 &&
        context.getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    override val saved: Boolean get() = prefs.contains(alias)

    private fun store(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build())
        return generator.generateKey()
    }

    private suspend fun authenticate(cipher: Cipher, title: String): Cipher? =
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(result.cryptoObject?.cipher)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            try {
                BiometricPrompt.Builder(context)
                    .setTitle(title)
                    .setSubtitle("Use your enrolled fingerprint or strong biometric")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .setNegativeButton("Cancel", context.mainExecutor) { _, _ ->
                        cancellation.cancel()
                        if (continuation.isActive) continuation.resume(null)
                    }
                    .build()
                    .authenticate(BiometricPrompt.CryptoObject(cipher), cancellation, context.mainExecutor, callback)
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    override suspend fun save(credential: String): Boolean {
        if (!available || saved || credential.isBlank()) return false
        var stored = false
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, createKey())
            val authorized = authenticate(cipher, "Save camera credential") ?: return false
            val plain = credential.toByteArray(Charsets.UTF_8)
            val encrypted = try { authorized.doFinal(plain) } finally { plain.fill(0) }
            val iv = authorized.iv
            if (iv.size != 12) return false
            val blob = Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
            stored = prefs.edit().putString(alias, blob).commit()
            return stored
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        } finally {
            if (!stored) forget()
        }
    }

    override suspend fun unlock(): String? {
        if (!available || !saved) return null
        try {
            val blob = Base64.decode(prefs.getString(alias, null) ?: return null, Base64.NO_WRAP)
            if (blob.size <= 28) return null
            val key = store().getKey(alias, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.copyOfRange(0, 12)))
            val authorized = authenticate(cipher, "Unlock camera") ?: return null
            val plain = authorized.doFinal(blob.copyOfRange(12, blob.size))
            return try { plain.toString(Charsets.UTF_8) } finally { plain.fill(0) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
    }

    override fun forget() {
        // Removing either the encrypted record or its key makes the credential unusable.
        prefs.edit().remove(alias).commit()
        runCatching { store().deleteEntry(alias) }
    }
}
