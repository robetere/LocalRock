package com.kodraliu.localrock.shared.webrtc

import com.kodraliu.localrock.shared.protocol.appContextOrNull
import com.kodraliu.localrock.MainActivity
import android.Manifest
import android.content.pm.PackageManager
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.RtpSender
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.Logging
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpCapabilities
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID

actual val liveViewSupported: Boolean = true

actual fun createRtcPeer(iceServers: List<RtcIceServer>): RtcPeer = AndroidRtcPeer(iceServers)

internal val rtcEglBase: EglBase by lazy { EglBase.create() }

private val peerConnectionFactory: PeerConnectionFactory by lazy {
    val ctx = requireNotNull(appContextOrNull()) { "Android context not initialized" }
    PeerConnectionFactory.initialize(
        PeerConnectionFactory.InitializationOptions.builder(ctx).createInitializationOptions()
    )
    // Native diagnostics can contain SDP/ICE. Use only the sanitized stats shown by the app.
    Logging.enableLogToDebugOutput(Logging.Severity.LS_NONE)
    PeerConnectionFactory.builder()
        .setVideoDecoderFactory(DefaultVideoDecoderFactory(rtcEglBase.eglBaseContext))
        .setVideoEncoderFactory(DefaultVideoEncoderFactory(rtcEglBase.eglBaseContext, true, true))
        .createPeerConnectionFactory()
}

internal class AndroidRtcPeer(iceServers: List<RtcIceServer>) : RtcPeer {

    private val _localCandidates = MutableSharedFlow<RtcIceCandidate>(replay = 32, extraBufferCapacity = 32)
    override val localCandidates: SharedFlow<RtcIceCandidate> = _localCandidates

    private val _connectionState = MutableStateFlow(RtcConnectionState.NEW)
    override val connectionState: StateFlow<RtcConnectionState> = _connectionState

    val videoTrack = MutableStateFlow<VideoTrack?>(null)

    private val pc: PeerConnection
    private val audioLock = Any()
    private var audioSender: RtpSender? = null
    private var microphoneSource: AudioSource? = null
    private var microphoneTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var voiceActive = false
    private var remoteMuted = true
    private var audioClosed = false

    override val supportsVoiceCall: Boolean = true
    override suspend fun requestMicrophonePermission(): Boolean = MainActivity.requestCameraMicrophone()

    override suspend fun startVoiceCall() {
        synchronized(audioLock) {
            check(!audioClosed && MainActivity.cameraAudioForeground())
            check(connectionState.value == RtcConnectionState.CONNECTED)
            val context = requireNotNull(appContextOrNull())
            check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            if (voiceActive) return
            val sender = requireNotNull(audioSender)
            // Never create or attach a microphone track during ordinary remote viewing.
            check(microphoneTrack == null)
            val source = peerConnectionFactory.createAudioSource(MediaConstraints())
            microphoneSource = source
            try {
                val track = peerConnectionFactory.createAudioTrack(UUID.randomUUID().toString(), source)
                microphoneTrack = track
                track.setEnabled(false)
                check(sender.setTrack(track, false))
                check(track.setEnabled(true))
                voiceActive = true
                remoteMuted = false
                remoteAudioTrack?.setEnabled(true)
            } catch (e: Exception) {
                stopVoiceCall()
                throw e
            }
        }
    }

    override fun setMicrophoneMuted(muted: Boolean) {
        synchronized(audioLock) {
            microphoneTrack?.setEnabled(voiceActive && !muted && !audioClosed)
        }
    }

    override fun setRemoteAudioMuted(muted: Boolean) {
        synchronized(audioLock) {
            remoteMuted = muted
            remoteAudioTrack?.setEnabled(voiceActive && !muted && !audioClosed)
        }
    }

    override fun stopVoiceCall() {
        synchronized(audioLock) {
            voiceActive = false
            remoteMuted = true
            runCatching { microphoneTrack?.setEnabled(false) }
            runCatching { remoteAudioTrack?.setEnabled(false) }
            // If detachment fails, retain the disabled track until peer shutdown.
            val detached = microphoneTrack == null ||
                runCatching { audioSender?.setTrack(null, false) == true }.getOrDefault(false)
            if (detached) disposeMicrophone()
        }
    }

    private fun disposeMicrophone() {
        runCatching { microphoneTrack?.dispose() }
        microphoneTrack = null
        runCatching { microphoneSource?.dispose() }
        microphoneSource = null
    }

    private fun receiveAudio(track: AudioTrack) {
        synchronized(audioLock) {
            if (audioClosed) return
            remoteAudioTrack = track
            track.setEnabled(voiceActive && !remoteMuted)
        }
    }

    init {
        val servers = iceServers.map { s ->
            PeerConnection.IceServer.builder(s.url)
                .apply {
                    if (s.username.isNotEmpty()) setUsername(s.username)
                    if (s.password.isNotEmpty()) setPassword(s.password)
                }
                .createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        pc = requireNotNull(peerConnectionFactory.createPeerConnection(config, Observer())) {
            "createPeerConnection returned null"
        }
    }

    override suspend fun createOffer(options: RtcOfferOptions): String {
        val video = MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO
        val audio = MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO
        for (kind in listOf(audio, video)) {
            val streams = if (kind == audio) listOf(UUID.randomUUID().toString()) else emptyList()
            // Metadata only: never attach a microphone/camera capture source or local track.
            val transceiver = requireNotNull(pc.addTransceiver(kind, RtpTransceiver.RtpTransceiverInit(
                RtpTransceiver.RtpTransceiverDirection.SEND_RECV, streams)))
            val codecs = peerConnectionFactory.getRtpReceiverCapabilities(kind).codecs
                .mapNotNull { codec -> codecRank(codec, options.compactCodecs)?.let { it to codec } }
                .sortedBy { it.first }.map { it.second }
            check(codecs.any { if (kind == audio) it.name.equals("opus", true) else
                it.name.equals("h264", true) && it.parameters["profile-level-id"]?.lowercase()?.startsWith("42e0") == true }) {
                "Required video offer codecs unavailable"
            }
            transceiver.setCodecPreferences(codecs)
            if (kind == audio) audioSender = transceiver.sender
        }
        val offer = suspendCancellableCoroutine<SessionDescription> { cont ->
            pc.createOffer(object : SdpObserverAdapter() {
                override fun onCreateSuccess(desc: SessionDescription) { if (cont.isActive) cont.resume(desc) }
                override fun onCreateFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(RuntimeException("Video offer creation failed"))
                }
            }, MediaConstraints())
        }
        suspendCancellableCoroutine<Unit> { cont ->
            pc.setLocalDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
                override fun onSetFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(RuntimeException("Local video description rejected"))
                }
            }, offer)
        }
        return offer.description
    }

    override suspend fun setRemoteAnswer(sdp: String) {
        suspendCancellableCoroutine<Unit> { cont ->
            pc.setRemoteDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
                override fun onSetFailure(error: String?) {
                    if (cont.isActive) cont.resumeWithException(RuntimeException("Video answer rejected"))
                }
            }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
        }
    }

    override suspend fun addRemoteCandidate(candidate: RtcIceCandidate) {
        check(pc.addIceCandidate(IceCandidate(candidate.sdpMid ?: "0", candidate.sdpMLineIndex, candidate.candidate))) {
            "Video candidate rejected"
        }
    }

    override suspend fun diagnostics(): RtcDiagnostics = suspendCancellableCoroutine { cont ->
        pc.getStats { report ->
            val stats = report.statsMap
            val video = stats.values.firstOrNull {
                it.type == "inbound-rtp" && (it.members["kind"] ?: it.members["mediaType"]) == "video"
            }
            val transport = (video?.members?.get("transportId") as? String)?.let { stats[it] }
            val pair = (transport?.members?.get("selectedCandidatePairId") as? String)?.let { stats[it] }
            val local = (pair?.members?.get("localCandidateId") as? String)?.let { stats[it] }
            val remote = (pair?.members?.get("remoteCandidateId") as? String)?.let { stats[it] }
            val frames = (video?.members?.get("framesDecoded") as? Number)?.toLong() ?: 0L
            val result = selectedPairDiagnostics(
                local?.members?.get("candidateType") as? String,
                remote?.members?.get("candidateType") as? String,
                local?.members?.get("protocol") as? String,
                frames > 0L,
                (video?.members?.get("frameWidth") as? Number)?.toInt() ?: 0,
                (video?.members?.get("frameHeight") as? Number)?.toInt() ?: 0,
                frames,
            )
            if (cont.isActive) cont.resume(result)
        }
    }

    override fun close() {
        synchronized(audioLock) {
            if (audioClosed) return
            audioClosed = true
            stopVoiceCall()
            remoteAudioTrack = null
        }
        videoTrack.value = null
        _connectionState.value = RtcConnectionState.CLOSED
        runCatching { pc.close() }
        runCatching { pc.dispose() }
        synchronized(audioLock) {
            disposeMicrophone()
            audioSender = null
        }
    }

    private inner class Observer : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            _localCandidates.tryEmit(
                RtcIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            )
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            when (val track = transceiver.receiver.track()) {
                is VideoTrack -> videoTrack.value = track
                is AudioTrack -> receiveAudio(track)
            }
        }

        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            when (val track = receiver.track()) {
                is VideoTrack -> videoTrack.value = track
                is AudioTrack -> receiveAudio(track)
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            // Don't clobber the terminal CLOSED state set by close().
            if (_connectionState.value == RtcConnectionState.CLOSED) return
            _connectionState.value = when (newState) {
                PeerConnection.PeerConnectionState.NEW -> RtcConnectionState.NEW
                PeerConnection.PeerConnectionState.CONNECTING -> RtcConnectionState.CONNECTING
                PeerConnection.PeerConnectionState.CONNECTED -> RtcConnectionState.CONNECTED
                PeerConnection.PeerConnectionState.DISCONNECTED -> RtcConnectionState.DISCONNECTED
                PeerConnection.PeerConnectionState.FAILED -> RtcConnectionState.FAILED
                PeerConnection.PeerConnectionState.CLOSED -> RtcConnectionState.CLOSED
            }
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {}
    }
}

private fun codecRank(codec: RtpCapabilities.CodecCapability, compact: Boolean): Int? {
    val name = codec.name.lowercase()
    if (codec.kind == MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO) {
        if (codec.clockRate != 48000) return null
        return when (name) {
            "opus" -> if (codec.numChannels == 2) 0 else null
            "red" -> if (!compact && codec.numChannels == 2) 1 else null
            "telephone-event" -> if (!compact) 2 else null
            else -> null
        }
    }
    if (codec.clockRate != 90000) return null
    return when (name) {
        "h264" -> {
            if (codec.parameters["packetization-mode"] != "1") return null
            val profile = codec.parameters["profile-level-id"]?.lowercase().orEmpty()
            when {
                profile.startsWith("640c") && !compact -> 0
                profile.startsWith("42e0") -> 1
                else -> null
            }
        }
        "vp8" -> if (!compact) 2 else null
        "vp9" -> if (!compact && (codec.parameters["profile-id"] ?: "0") == "0") 3 else null
        "av1" -> if (!compact && (codec.parameters["profile"] ?: "0") == "0") 4 else null
        "red" -> if (!compact) 5 else null
        "ulpfec" -> if (!compact) 6 else null
        "rtx" -> 7
        else -> null
    }
}

private abstract class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {}
    override fun onSetFailure(error: String?) {}
}

