package com.kodraliu.localrock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import java.lang.ref.WeakReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kodraliu.localrock.shared.AppContainer
import com.kodraliu.localrock.shared.notify.RobotNotifications
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer
    private var pendingMicrophonePermission: CompletableDeferred<Boolean>? = null
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingMicrophonePermission?.complete(granted)
        pendingMicrophonePermission = null
    }

    companion object {
        private var activeActivity: WeakReference<MainActivity>? = null

        internal suspend fun requestCameraMicrophone(): Boolean = withContext(Dispatchers.Main.immediate) {
            val activity = activeActivity?.get() ?: return@withContext false
            if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                return@withContext true
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                activity.pendingMicrophonePermission != null) return@withContext false
            val pending = CompletableDeferred<Boolean>()
            activity.pendingMicrophonePermission = pending
            try { activity.microphonePermission.launch(Manifest.permission.RECORD_AUDIO) }
            catch (_: Exception) {
                activity.pendingMicrophonePermission = null
                return@withContext false
            }
            // A cancelled call cannot consume a later permission result or start another call.
            pending.await()
        }

        internal fun cameraAudioForeground(): Boolean =
            activeActivity?.get()?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        activeActivity = WeakReference(this)

        container = (application as LocalRockApp).container
        openRobotFrom(intent)

        // Android only allows a foreground service to be started while the app is on screen, so
        // the background watch is brought in line with the setting here rather than from the setting.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.backgroundWatchWanted.collect { wanted ->
                    RobotMonitorService.sync(this@MainActivity, wanted)
                }
            }
        }

        setContent { App(container) }
    }

    override fun onDestroy() {
        pendingMicrophonePermission?.complete(false)
        pendingMicrophonePermission = null
        if (activeActivity?.get() === this) activeActivity = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRobotFrom(intent)
    }

    /** A tapped robot notification carries the robot it is about. */
    private fun openRobotFrom(intent: Intent?) {
        val duid = intent?.getStringExtra(RobotNotifications.EXTRA_OPEN_DUID) ?: return
        intent.removeExtra(RobotNotifications.EXTRA_OPEN_DUID)
        container.requestOpenDevice(duid)
    }
}
