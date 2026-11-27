package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.camera.CameraLiveSession
import com.kodraliu.localrock.shared.vacuum.camera.CameraVideoQuality
import com.kodraliu.localrock.shared.vacuum.camera.CameraMotionController
import com.kodraliu.localrock.shared.vacuum.camera.CameraVoiceCall
import com.kodraliu.localrock.shared.webrtc.RtcPeer
import com.kodraliu.localrock.shared.webrtc.RtcDiagnostics
import com.kodraliu.localrock.shared.webrtc.RtcVideoView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

@Composable
internal fun CameraVideoControls(
    viewModel: VacuumViewModel, peer: RtcPeer?, motion: CameraMotionController,
    state: CameraLiveSession.State, ready: Boolean, expanded: Boolean,
    controlsAllowed: Boolean, sarosDriveTest: Boolean = false, driveReady: Boolean = ready,
    voice: CameraVoiceCall,
    onExpand: (Boolean) -> Unit, onStopVideo: () -> Unit, onDock: () -> Unit,
    onRetry: () -> Unit, quality: CameraVideoQuality, qualityEnabled: Boolean, qualityError: String?,
    diagnostics: RtcDiagnostics, onQuality: (CameraVideoQuality) -> Unit, onRetrySd: () -> Unit,
) {
    val status by viewModel.status.collectAsState()
    val map by viewModel.repository.parsedMap.collectAsState()
    val floor by viewModel.repository.currentFloorFlag.collectAsState()
    val busy by motion.busy.collectAsState()
    val error by motion.error.collectAsState()
    val controlProgress by motion.progress.collectAsState()
    val callActive by voice.active.collectAsState()
    val callBusy by voice.busy.collectAsState()
    val callError by voice.error.collectAsState()
    val microphoneMuted by voice.microphoneMuted.collectAsState()
    val remoteAudioMuted by voice.remoteAudioMuted.collectAsState()
    val speakerVolume by voice.speakerVolume.collectAsState()
    val volumeBusy by voice.volumeBusy.collectAsState()
    val callsSupported = peer?.supportsVoiceCall == true
    var steering by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    // Preserve the tested-model default; experimental viewers start with the full frame.
    var fillVideo by remember { mutableStateOf(controlsAllowed) }
    var showMiniMap by remember { mutableStateOf(false) }
    var cockpitMode by remember { mutableStateOf(CockpitMode.VIEWER) }
    var details by remember { mutableStateOf(false) }
    var gamepadSelected by remember { mutableStateOf(false) }
    var padLayout by remember { mutableStateOf(CameraPadLayout.RACING) }
    var settingsSection by remember { mutableStateOf(CameraSettingsSection.CAMERA) }
    val connectedPad = cameraConnectedGamepad()
    val mapMode = sarosDriveTest && cockpitMode == CockpitMode.MAP
    val showDriveTest = cockpitMode == CockpitMode.PILOT || mapMode
    fun changeMode(mode: CockpitMode) { motion.release(); gamepadSelected=false; cockpitMode = mode }
    val gamepadStatus = CameraGamepadSession(motion, sarosDriveTest && showDriveTest && gamepadSelected && driveReady && !callBusy && !settings && !details,
        padLayout)
    var mapExpanded by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableStateOf(0) }
    var target by remember(floor, map?.viewportIdentity(floor)) { mutableStateOf<Pair<Int,Int>?>(null) }
    // Navigation is independent of video: a docked robot must be able to leave through Go.
    val enabled = controlsAllowed && !busy
    val docked = status.state in setOf(VacuumStateCodes.CHARGING, VacuumStateCodes.CHARGING_COMPLETE)
    val headline = if (status.state == VacuumStateCodes.CHARGING_COMPLETE) "Charged"
        else status.state?.let { VacuumStateCodes.describe(it) } ?: "Robot status unavailable"
    fun interact() { controlsVisible = true; interaction++ }
    val callLabel = if (callBusy) "Cancel call" else if (callActive) "End call" else "Start call"
    val callEnabled = callActive || callBusy || (ready && callsSupported)
    val toggleCall: () -> Unit = {
        motion.release()
        if (callActive || callBusy) voice.stop() else voice.start()
    }
    val callButton: @Composable (Modifier) -> Unit = { modifier ->
        FilledTonalButton(onClick=toggleCall,enabled=callEnabled,modifier=modifier,
            colors=ButtonDefaults.filledTonalButtonColors(
                containerColor=if(callActive) Color(0xff168658) else Color(0xff303030),contentColor=Color.White)) {
            Icon(if(callActive || callBusy) Icons.Default.CallEnd else Icons.Default.Mic,null)
            Spacer(Modifier.width(6.dp))
            Text(callLabel)
        }
    }
    val callIconButton: @Composable (Modifier) -> Unit = { modifier ->
        FilledTonalIconButton(onClick=toggleCall,enabled=callEnabled,modifier=modifier.size(56.dp),
            colors=IconButtonDefaults.filledTonalIconButtonColors(
                containerColor=if(callActive || callBusy) Color(0xffb3261e) else Color(0xff168658),
                contentColor=Color.White)) {
            Icon(if(callActive || callBusy) Icons.Default.CallEnd else Icons.Default.Mic,callLabel)
        }
    }
    LaunchedEffect(expanded, interaction, busy, mapExpanded, settings) {
        controlsVisible = true
        if (expanded && !busy && !mapExpanded && !settings) {
            delay(5_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(expanded) { if (!expanded) mapExpanded = false }
    LaunchedEffect(ready, settings) {
        if (!ready) changeMode(CockpitMode.VIEWER)
        else if (settings) motion.release()
    }
    LaunchedEffect(viewModel, ready, status.state) {
        if (controlsAllowed && ready && status.state == VacuumStateCodes.REMOTE_CONTROL_ACTIVE) {
            while (isActive) {
                try { viewModel.repository.fetchMap() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Keep the last map while video remains usable. */ }
                delay(3_000)
            }
        }
    }
    fun go() {
        if (!controlsAllowed) return
        val point = target ?: return
        val current = map ?: return
        val cell = current.mmToCell(point.first,point.second)
        if (cameraTargetAt(current,Offset(cell.x/current.width,cell.y/current.height)) != null) {
            motion.goTo(point.first,point.second,floor)
            mapExpanded = false
        } else target = null
    }
    val targetPanel: @Composable (Modifier, Boolean) -> Unit = { modifier, canClose ->
        Column(modifier.background(Color(0xffe9edf1))) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("Tap the map to set a target",Modifier.weight(1f),color=Color.DarkGray)
                if (canClose) IconButton(onClick={mapExpanded=false;interact()}) { Icon(Icons.Default.Close,"Close expanded map",tint=Color.Black) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                val pm=map
                if (pm==null) Text("Waiting for the map…",color=Color.DarkGray)
                else CameraTargetMap(pm,floor,target,enabled,{target=it},Modifier.fillMaxSize())
            }
            Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.End) {
                Button(onClick={go()},enabled=enabled && target!=null) {
                    Icon(Icons.Default.NearMe,null);Spacer(Modifier.width(6.dp));Text("Go")
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
        // Observe interaction without consuming map gestures or hold-to-drive pointer events.
        awaitPointerEventScope {
            while (true) {
                val event=awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any {it.pressed}) interact()
            }
        }
    }) {
        // Folding, unfolding or rotating can change control geometry under an active
        // gesture. Stop motion and require fresh input without recreating the video peer.
        var previousViewport by remember { mutableStateOf<Pair<Float, Float>?>(null) }
        LaunchedEffect(maxWidth, maxHeight) {
            val viewport = maxWidth.value to maxHeight.value
            if (previousViewport != null && previousViewport != viewport && sarosDriveTest) {
                motion.release()
                gamepadSelected = false
            }
            previousViewport = viewport
        }
        val landscape = maxWidth > maxHeight
        val wide = expanded && landscape
        val overlayInsets = if (expanded) WindowInsets.safeDrawing else WindowInsets(0, 0, 0, 0)
        val portraitVideoHeight = maxHeight * .43f
        val miniMapSize = minOf(maxWidth * .28f, maxHeight * .19f, 116.dp)
        val pilotPadSize = if (mapMode) minOf(maxWidth * .36f, maxHeight * .23f, 140.dp)
            else minOf(maxWidth * .44f, maxHeight * .33f, 180.dp)
        // Reserve side rails for controls on unfolded / wide displays.
        val mapSideRails = mapMode && maxWidth >= 520.dp && maxWidth > maxHeight * .78f && !gamepadSelected
        val mapRailWidth = if (mapSideRails) pilotPadSize + 16.dp else 0.dp
        val mapVideoHeight = maxHeight * (if (gamepadSelected || mapSideRails) .38f else .34f)
        val videoModifier = if (mapMode) Modifier.fillMaxWidth().height(mapVideoHeight)
            else if (expanded || !controlsAllowed) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(portraitVideoHeight)
        // Keep one native renderer composition site through expansion, rotation and map changes.
        Box(videoModifier.background(Color.Black).clipToBounds()) {
            peer?.let { currentPeer -> key(currentPeer) { RtcVideoView(currentPeer,Modifier.fillMaxSize(),fill=!mapMode && fillVideo && (wide || !controlsAllowed)) } }
            if (docked && controlsAllowed) {
                Text("Robot docked. Choose a spot on the map and tap Go to move it and start video.",
                    Modifier.align(Alignment.Center).padding(horizontal=32.dp,vertical=72.dp),
                    color=Color.White,textAlign=TextAlign.Center)
            } else when (val current=state) {
                is CameraLiveSession.State.Connecting -> Text(current.stage,
                    Modifier.align(Alignment.Center).background(Color.Black.copy(alpha=.65f)).padding(12.dp),color=Color.White)
                is CameraLiveSession.State.Failed -> Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(current.message,color=Color.White,textAlign=TextAlign.Center)
                    Button(onClick=onRetry) { Text("Retry video") }
                    if(quality==CameraVideoQuality.HD) TextButton(onClick=onRetrySd) {Text("Retry in SD")}
                }
                else -> Unit
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .background(Color.Black.copy(alpha=.55f)).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(status.battery?.let {"$it%"} ?: "—%",Modifier.padding(horizontal=8.dp),color=Color.White)
                Text(headline,Modifier.weight(1f),color=Color.White,textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
                if (sarosDriveTest) {
                    Icon(if (ready) Icons.Default.CheckCircle else Icons.Default.Warning,
                        if (ready) "Video connected" else "Video not connected",
                        Modifier.size(18.dp),tint=if (ready) Color(0xff68d9a2) else Color(0xffffca75))
                }
                if (controlsVisible || !expanded) {
                    if (!sarosDriveTest) TextButton(onClick={motion.release();settings=true}) { Text("Options",color=Color.White) }
                }
                // Keep the exit reachable even after the driving overlays fade.
                if (expanded) {
                    TextButton(onClick={motion.release();onExpand(false)}) {
                        Icon(Icons.Default.FullscreenExit,null,tint=Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("Exit",color=Color.White)
                    }
                } else IconButton(onClick={motion.release();onExpand(true)}) {
                    Icon(Icons.Default.Fullscreen,"Expand video",tint=Color.White)
                }
                if (controlsVisible || !expanded) {
                    IconButton(onClick={motion.release();settings=true}) { Icon(if(sarosDriveTest) Icons.Default.Settings else Icons.Default.MoreVert,"Video settings",tint=Color.White) }
                }
            }
            if (!controlsAllowed && showMiniMap && !mapMode) {
                Column(Modifier.align(Alignment.TopStart)
                    .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Start))
                    .padding(top=58.dp,start=8.dp).width(miniMapSize)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xff171c20).copy(alpha=.88f))) {
                    Box(Modifier.fillMaxWidth().height(miniMapSize)
                        .background(Color(0xffe9edf1)),contentAlignment=Alignment.Center) {
                        val currentMap = map
                        if (currentMap == null) Text("No map",color=Color.DarkGray)
                        else CameraTargetMap(currentMap,floor,null,false,{},Modifier.fillMaxSize(), gestures=false)
                        if (sarosDriveTest) Box(Modifier.fillMaxSize().clickable { changeMode(CockpitMode.MAP) }
                            .semantics { contentDescription="Open last map" })
                    }
                    Text("Last map",Modifier.fillMaxWidth().padding(4.dp),color=Color.White,
                        textAlign=TextAlign.Center,style=MaterialTheme.typography.labelSmall)
                }
            }
            if (!expanded && controlsAllowed) CameraCleaningButton(viewModel,motion,modifier=Modifier.align(Alignment.BottomEnd).padding(8.dp))
            if (!expanded && controlsAllowed) callIconButton(Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        if (!controlsAllowed) {
            if (mapMode) {
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(mapVideoHeight))
                    Column(Modifier.fillMaxWidth().weight(1f).background(Color(0xff171c20))
                        .padding(8.dp)) {
                        CameraReadOnlyMap(
                            map = map,
                            floor = floor,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            sidePadding = mapRailWidth,
                        )
                        // On wide displays controls sit beside the map; on narrow displays below it.
                        Spacer(Modifier.height(86.dp + if (mapSideRails) 0.dp else if (gamepadSelected) 48.dp else pilotPadSize + 8.dp))
                    }
                }
            }
            if (sarosDriveTest && showDriveTest && !settings) {
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(start=12.dp,end=12.dp,bottom=80.dp),
                    horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom) {
                    if (!gamepadSelected) CameraPilotJoystick(motion,driveReady && !callBusy,Modifier.size(pilotPadSize))
                    else Spacer(Modifier.size(1.dp))
                    if (cameraGamepadSupported) FilledTonalButton(
                        onClick={motion.release();gamepadSelected=!gamepadSelected},
                        colors=ButtonDefaults.filledTonalButtonColors(
                            containerColor=Color(0xff171c20).copy(alpha=.86f),contentColor=Color.White)
                    ) { Text(if(gamepadSelected) "Touch" else if(connectedPad!=null) "Use controller" else "Controller") }
                }
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal=8.dp,vertical=8.dp).clip(RoundedCornerShape(18.dp))
                .background(Color(0xff171c20).copy(alpha=.90f)).padding(horizontal=4.dp,vertical=4.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
                if (sarosDriveTest) {
                    CockpitMode.entries.forEach { mode ->
                        val label = when(mode) {
                            CockpitMode.VIEWER -> "View"
                            CockpitMode.PILOT -> "Pilot"
                            CockpitMode.MAP -> "Map"
                        }
                        TextButton(onClick={changeMode(mode)},
                            enabled=mode != CockpitMode.PILOT || ready,
                            contentPadding=PaddingValues(horizontal=5.dp),
                            modifier=Modifier.weight(1f)) {
                            Text(label,color=if(cockpitMode==mode) Color(0xff68d9a2) else Color.White,
                                style=MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                callIconButton(Modifier)
                IconButton(onClick={motion.release();onStopVideo()}) {
                    Icon(Icons.Default.Close,"End video",tint=Color.White)
                }
            }
        } else if (wide) {
            if (mapExpanded) {
                targetPanel(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(.6f)
                    .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)).padding(top=48.dp),true)
            } else {
                Box(Modifier.align(Alignment.TopStart).windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Start))
                    .padding(top=56.dp,start=12.dp)
                    .size((maxHeight * .30f).coerceAtMost(150.dp))) {
                    map?.let {pm->CameraTargetMap(pm,floor,null,false,{},Modifier.fillMaxSize())}
                    // The overlay owns the tap; the thumbnail itself must not pan behind it.
                    Box(Modifier.fillMaxSize().semantics { contentDescription="Expand map for Pin-and-Go";onClick {motion.release();mapExpanded=true;true} }
                        .pointerInput(Unit) {detectTapGestures(onTap={motion.release();mapExpanded=true})})
                }
                if (controlsVisible) CameraDrivePad(motion,driveReady,
                    Modifier.align(Alignment.BottomStart).windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start))
                        .padding(16.dp).size((maxHeight*.48f).coerceAtMost(220.dp)))
            }
            // Keep call controls in the right rail, outside the expanded map and its Go button.
            Column(Modifier.align(Alignment.BottomEnd)
                .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                .padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if (controlsVisible) {
                    CameraCleaningButton(viewModel,motion,compact=true)
                    FilledTonalIconButton(onClick=onDock,enabled=enabled,modifier=Modifier.size(56.dp)) { Icon(Icons.Default.Home,"Return to dock and end video") }
                }
                callIconButton(Modifier)
            }
        } else if (expanded) {
            if (controlsVisible) Text("Turn your phone sideways for full-screen controls.",
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Bottom))
                    .padding(bottom=64.dp).fillMaxWidth().background(Color.Black.copy(alpha=.6f)).padding(16.dp),color=Color.White)
        } else {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(portraitVideoHeight))
                Row(Modifier.fillMaxWidth().background(Color(0xff242424)).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={motion.release();steering=!steering}) { Text(if(steering) "Map" else "Controls",color=Color.White) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick={motion.stop()}) { Icon(Icons.Default.Stop,"Stop movement",tint=Color.White) }
                    IconButton(onClick=onDock,enabled=enabled) { Icon(Icons.Default.Home,"Return to dock and end video",tint=if(enabled) Color.White else Color.Gray) }
                    TextButton(onClick=onStopVideo) { Text("End video",color=Color.White) }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).background(Color(0xff181818)),contentAlignment=Alignment.Center) {
                    if (steering) {
                        val padSize=minOf(maxWidth*.85f,(maxHeight-40.dp).coerceAtLeast(48.dp))
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            CameraDrivePad(motion,driveReady,Modifier.size(padSize))
                            Text("Hold to drive · release to stop",color=Color.LightGray,modifier=Modifier.padding(8.dp))
                        }
                    } else targetPanel(Modifier.fillMaxSize(),false)
                }
            }
        }
        if (expanded && !wide && controlsAllowed) {
            callIconButton(Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(bottom=12.dp))
        }
        val issue = error ?: callError
        if (sarosDriveTest) {
            val message = when {
                issue != null -> if (error != null) "Drive needs attention" else "Call needs attention"
                controlProgress != null -> controlProgress
                showDriveTest -> if (!ready) "Video disconnected" else if (!driveReady) "Waiting for fresh video · driving disabled" else if (gamepadSelected) gamepadStatus else "Hold joystick to drive"
                else -> null
            }
            if (message != null) Row(Modifier.align(Alignment.TopCenter)
                .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Top))
                .padding(top=56.dp,start=8.dp,end=8.dp).clip(RoundedCornerShape(10.dp))
                .background(if(issue!=null) Color(0xff702e2e) else Color(0xff171c20).copy(alpha=.85f))
                .padding(horizontal=10.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(message,color=Color.White,style=MaterialTheme.typography.labelMedium)
                if (issue != null) TextButton(onClick={motion.release();changeMode(CockpitMode.VIEWER);details=true},
                    contentPadding=PaddingValues(horizontal=8.dp)) {Text("Details",color=Color.White)}
            }
        } else (issue ?: controlProgress)?.let {Text(it,Modifier.align(Alignment.TopCenter)
            .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Top)).padding(top=52.dp)
            .background(if (issue != null) Color(0xff5b2424) else Color(0xff242424))
            .padding(8.dp),color=Color.White)}
    }
    if(details) AlertDialog(onDismissRequest={details=false},title={Text("Control details")},
        text={Text(error ?: callError ?: "No current error")},
        confirmButton={TextButton(onClick={details=false}){Text("Close")}})
    if (settings) {
        CameraSettingsDialog(
            settingsSection = settingsSection,
            onSectionChange = { settingsSection = it },
            controlsAllowed = controlsAllowed,
            sarosDriveTest = sarosDriveTest,
            fillVideo = fillVideo,
            onFillVideoChange = { fillVideo = it },
            showMiniMap = showMiniMap,
            onMiniMapChange = { showMiniMap = it },
            callsSupported = callsSupported,
            callActive = callActive,
            callBusy = callBusy,
            callError = callError,
            microphoneMuted = microphoneMuted,
            remoteAudioMuted = remoteAudioMuted,
            speakerVolume = speakerVolume,
            volumeBusy = volumeBusy,
            voice = voice,
            callButton = callButton,
            connectedPad = connectedPad,
            padLayout = padLayout,
            onPadLayoutChange = { padLayout = it },
            ready = ready,
            onUseController = {
                motion.release()
                if (cockpitMode == CockpitMode.VIEWER) cockpitMode = CockpitMode.PILOT
                gamepadSelected = true
                settings = false
            },
            diagnostics = diagnostics,
            qualityError = qualityError,
            onDismiss = { settings = false },
        )
    }
}

private enum class CockpitMode { VIEWER, PILOT, MAP }
