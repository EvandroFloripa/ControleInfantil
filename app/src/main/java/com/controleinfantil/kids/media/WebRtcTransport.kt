package com.controleinfantil.kids.media

import android.content.Context
import android.util.Log
import com.controleinfantil.kids.remote.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.Camera2Enumerator
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource

/**
 * Transporte WebRTC do lado do APARELHO — o lado que tem a mídia, então é quem cria
 * a oferta. A troca de oferta/resposta/ICE passa pela tabela `signals` do Supabase.
 *
 * A parte visível (notificação, bolinha do Android) fica nos serviços que criam este
 * transporte; aqui é só a conexão. Não há caminho oculto: sem os serviços em primeiro
 * plano, a captura nem começa.
 *
 * NÃO TESTADO em aparelho — escrito para ser o ponto de partida do WebRTC. Espere
 * ajustes (versão da lib, nomes de API, TURN) na primeira execução real.
 */
class WebRtcTransport(context: Context) : MediaTransport {

    private val appContext = context.applicationContext
    private val client = SupabaseClient(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val eglBase: EglBase = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var surfaceHelper: SurfaceTextureHelper? = null

    private var session: MediaTransport.SessionConfig? = null
    private var lastSignalId = 0L

    @Volatile
    private var stopped = false

    override fun start(session: MediaTransport.SessionConfig) {
        this.session = session
        ensureFactory(appContext)
        val factory = this.factory ?: run {
            Log.e(TAG, "PeerConnectionFactory indisponível")
            return
        }

        val pc = factory.createPeerConnection(
            PeerConnection.RTCConfiguration(WebRtcConfig.iceServers()).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            },
            pcObserver,
        ) ?: run {
            Log.e(TAG, "Falha ao criar PeerConnection")
            return
        }
        peerConnection = pc

        when (session.kind) {
            MediaTransport.Kind.CAMERA -> addCameraTracks(factory, pc)
            MediaTransport.Kind.SCREEN -> addScreenTrack(factory, pc, session)
        }

        createAndSendOffer(pc)
        startSignalingLoop(session.sessionId)
    }

    // --- Faixas -------------------------------------------------------------

    private fun addCameraTracks(factory: PeerConnectionFactory, pc: PeerConnection) {
        val capturer = createCameraCapturer() ?: run {
            Log.e(TAG, "Nenhuma câmera frontal encontrada")
            return
        }
        videoCapturer = capturer
        val source = factory.createVideoSource(false)
        videoSource = source
        surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        capturer.initialize(surfaceHelper, appContext, source.capturerObserver)
        capturer.startCapture(1280, 720, 30)
        pc.addTrack(factory.createVideoTrack("video", source), listOf(STREAM_ID))

        val audio = factory.createAudioSource(MediaConstraints())
        audioSource = audio
        pc.addTrack(factory.createAudioTrack("audio", audio), listOf(STREAM_ID))
    }

    private fun addScreenTrack(
        factory: PeerConnectionFactory,
        pc: PeerConnection,
        session: MediaTransport.SessionConfig,
    ) {
        val data = session.screenData ?: run {
            Log.e(TAG, "Sem dados de consentimento de tela")
            return
        }
        val capturer = ScreenCapturerAndroid(data, object : android.media.projection.MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "Projeção de tela encerrada pelo sistema")
                stop()
            }
        })
        videoCapturer = capturer
        val source = factory.createVideoSource(true)  // isScreencast
        videoSource = source
        surfaceHelper = SurfaceTextureHelper.create("ScreenThread", eglBase.eglBaseContext)
        capturer.initialize(surfaceHelper, appContext, source.capturerObserver)
        capturer.startCapture(1280, 720, 15)
        pc.addTrack(factory.createVideoTrack("screen", source), listOf(STREAM_ID))
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(appContext)
        val frontal = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: return null
        return enumerator.createCapturer(frontal, null)
    }

    // --- Oferta e sinalização ----------------------------------------------

    private fun createAndSendOffer(pc: PeerConnection) {
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                scope.launch {
                    client.postSignal(
                        requireSession(), "offer",
                        JSONObject().put("type", "offer").put("sdp", sdp.description),
                    )
                }
            }
        }, MediaConstraints())
    }

    private fun startSignalingLoop(sessionId: String) {
        scope.launch {
            while (isActive) {
                client.fetchSignals(sessionId, lastSignalId).forEach { handleSignal(it) }
                delay(POLL_MS)
            }
        }
    }

    private fun handleSignal(signal: SupabaseClient.Signal) {
        lastSignalId = maxOf(lastSignalId, signal.id)
        val pc = peerConnection ?: return
        when (signal.kind) {
            "answer" -> pc.setRemoteDescription(
                SimpleSdpObserver(),
                SessionDescription(
                    SessionDescription.Type.ANSWER,
                    signal.payload.getString("sdp"),
                ),
            )
            "candidate" -> pc.addIceCandidate(
                IceCandidate(
                    signal.payload.getString("sdpMid"),
                    signal.payload.getInt("sdpMLineIndex"),
                    signal.payload.getString("candidate"),
                )
            )
            "bye" -> stop()
        }
    }

    private val pcObserver = object : SimplePcObserver() {
        override fun onIceCandidate(candidate: IceCandidate) {
            scope.launch {
                client.postSignal(
                    requireSession(), "candidate",
                    JSONObject()
                        .put("sdpMid", candidate.sdpMid)
                        .put("sdpMLineIndex", candidate.sdpMLineIndex)
                        .put("candidate", candidate.sdp),
                )
            }
        }
    }

    private fun requireSession(): String = session?.sessionId.orEmpty()

    @Synchronized
    override fun stop() {
        if (stopped) return
        stopped = true
        // "bye" em thread próprio: o escopo é cancelado logo abaixo e engoliria o envio.
        val session = requireSession()
        if (session.isNotEmpty()) {
            Thread { runCatching { client.postSignal(session, "bye", JSONObject()) } }.start()
        }
        runCatching { videoCapturer?.stopCapture() }
        videoCapturer?.dispose()
        videoSource?.dispose()
        audioSource?.dispose()
        surfaceHelper?.dispose()
        peerConnection?.dispose()
        videoCapturer = null
        peerConnection = null
        scope.cancel()
        eglBase.release()
    }

    companion object {
        private const val TAG = "WebRtcTransport"
        private const val STREAM_ID = "controle_infantil"
        private const val POLL_MS = 1500L

        @Volatile
        private var factoryInitialized = false

        @Synchronized
        private fun initGlobalFactory(context: Context) {
            if (factoryInitialized) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                    .builder(context.applicationContext)
                    .createInitializationOptions()
            )
            factoryInitialized = true
        }
    }

    private fun ensureFactory(context: Context) {
        if (factory != null) return
        initGlobalFactory(context)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }
}

/** Implementação vazia de [SdpObserver] para reduzir o ruído das quatro callbacks. */
private open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) {
        Log.e("WebRtcTransport", "SDP create falhou: $error")
    }
    override fun onSetFailure(error: String?) {
        Log.e("WebRtcTransport", "SDP set falhou: $error")
    }
}

/** Observer de PeerConnection com implementações vazias, exceto onIceCandidate. */
private open class SimplePcObserver : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(candidate: IceCandidate) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
    override fun onAddStream(stream: org.webrtc.MediaStream?) {}
    override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
    override fun onDataChannel(channel: org.webrtc.DataChannel?) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {}
    override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {}
}
