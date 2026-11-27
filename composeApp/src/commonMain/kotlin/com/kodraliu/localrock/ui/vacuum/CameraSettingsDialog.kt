package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kodraliu.localrock.shared.vacuum.camera.CameraVoiceCall
import com.kodraliu.localrock.shared.webrtc.RtcDiagnostics

/** Presents settings; the caller owns the viewer, controller selection and motion lifecycle. */
@Composable
internal fun CameraSettingsDialog(
    settingsSection: CameraSettingsSection,
    onSectionChange: (CameraSettingsSection) -> Unit,
    controlsAllowed: Boolean,
    sarosDriveTest: Boolean,
    fillVideo: Boolean,
    onFillVideoChange: (Boolean) -> Unit,
    showMiniMap: Boolean,
    onMiniMapChange: (Boolean) -> Unit,
    callsSupported: Boolean,
    callActive: Boolean,
    callBusy: Boolean,
    callError: String?,
    microphoneMuted: Boolean,
    remoteAudioMuted: Boolean,
    speakerVolume: Int?,
    volumeBusy: Boolean,
    voice: CameraVoiceCall,
    callButton: @Composable (Modifier) -> Unit,
    connectedPad: String?,
    padLayout: CameraPadLayout,
    onPadLayoutChange: (CameraPadLayout) -> Unit,
    ready: Boolean,
    onUseController: () -> Unit,
    diagnostics: RtcDiagnostics,
    qualityError: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest={onDismiss()},title={Text("Camera settings")},
        modifier=if (settingsSection == CameraSettingsSection.CONTROLLER)
            Modifier.widthIn(max=780.dp).fillMaxWidth(.94f) else Modifier,
        properties=DialogProperties(usePlatformDefaultWidth=settingsSection != CameraSettingsSection.CONTROLLER),
        text={Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            val sections = if (sarosDriveTest && cameraGamepadSupported) CameraSettingsSection.entries
                else CameraSettingsSection.entries.filter {it!=CameraSettingsSection.CONTROLLER}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                sections.filter {it!=CameraSettingsSection.DIAGNOSTICS}.forEach {section ->
                    FilterChip(selected=settingsSection==section,onClick={onSectionChange(section)},
                        label={Text(section.label,style=MaterialTheme.typography.labelSmall)})
                }
            }
            when(settingsSection) {
                CameraSettingsSection.CAMERA -> {
                    Text("Video framing",style=MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected=!fillVideo,onClick={onFillVideoChange(false)},label={Text("Fit")})
                        FilterChip(selected=fillVideo,onClick={onFillVideoChange(true)},label={Text("Fill")})
                    }
                    Text("Fit preserves the whole frame. Fill crops the edges.",style=MaterialTheme.typography.bodySmall)
                    if (!controlsAllowed) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Mini-map")
                            Text("Show last available map",style=MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked=showMiniMap,onCheckedChange={onMiniMapChange(it)})
                    }
                }
                CameraSettingsSection.AUDIO -> {
                    if (callsSupported) {
                        Text(if(callActive) "Call active" else if(callBusy) "Connecting…" else "Call off",
                            style=MaterialTheme.typography.titleSmall)
                        callButton(Modifier.fillMaxWidth())
                        if(callActive) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Text("Phone microphone",Modifier.weight(1f))
                                Switch(checked=!microphoneMuted,onCheckedChange={voice.muteMicrophone(!it)})
                            }
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Text("Listen to robot",Modifier.weight(1f))
                                Switch(checked=!remoteAudioMuted,onCheckedChange={voice.muteRemoteAudio(!it)})
                            }
                            Text("Robot speaker volume" + (speakerVolume?.let { ": $it%" } ?: ""))
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                listOf(25,50,75,100).forEach {value->
                                    FilterChip(selected=speakerVolume==value,onClick={voice.setSpeakerVolume(value)},
                                        enabled=!volumeBusy,label={Text("$value%",style=MaterialTheme.typography.labelSmall)})
                                }
                            }
                        }
                        Text("Audio is not saved. Use phone volume buttons to adjust listening volume.",style=MaterialTheme.typography.bodySmall)
                    } else Text("Voice calls are unavailable for this connection.")
                    callError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                }
                CameraSettingsSection.CONTROLLER -> {
                    CameraControllerSettings(connectedPad, padLayout, onPadLayoutChange, ready, onUseController)
                }
                CameraSettingsSection.DIAGNOSTICS -> {
                    Text("Connection",style=MaterialTheme.typography.titleSmall)
                    if(diagnostics.videoWidth>0 && diagnostics.videoHeight>0)
                        Text("Received video: ${diagnostics.videoWidth} × ${diagnostics.videoHeight}")
                    Text(diagnostics.summary)
                    qualityError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
                    Text("Remote viewing activation",style=MaterialTheme.typography.titleSmall)
                    Text("Activate remote viewing in the official Roborock app before switching to the local server. Activation is not available here.",
                        style=MaterialTheme.typography.bodySmall)
                }
            }
            if(settingsSection!=CameraSettingsSection.DIAGNOSTICS) {
                HorizontalDivider()
                TextButton(onClick={onSectionChange(CameraSettingsSection.DIAGNOSTICS)}) {Text("Advanced diagnostics")}
            }
        }},
        confirmButton={TextButton(onClick={onDismiss()}){Text("Done")}},
    )

}

internal enum class CameraSettingsSection(val label: String) {
    CAMERA("Camera"), AUDIO("Audio"), CONTROLLER("Controller"), DIAGNOSTICS("Diagnostics")
}

