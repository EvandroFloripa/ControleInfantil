package com.controleinfantil.kids.media

import android.content.Context
import android.util.Log
import com.controleinfantil.kids.remote.SupabaseClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONException
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
 * A câmera/tela só é ligada quando o responsável de fato conecta, e a sessão tem
 * prazos: para atender, para voltar de uma queda e uma duração máxima.
 *
 * NÃO TESTADO em aparelho — escrito para ser o ponto de partida do WebRTC. Espere
 * ajustes (versão da lib, nomes de API, TURN) na primeira execução real.
 */
class WebRtcTransport(
    context: Context,
    private val onClosed: () -> Unit = {},
) : MediaTransport {

    private val appContext = context.applicationContext
    private val client = SupabaseClient(appContext)

    // Um erro inesperado numa corrotina (ex.: sinal malformado) não pode derrubar o
    // processo, que é o mesmo do quiosque e do serviço de comandos: encerra a sessão.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Erro na sessão WebRTC; encerrando", e)
            stopAsync()
        }
    )

    private val eglBase: EglBase = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var surfaceHelper: SurfaceTextureHelper? = null

    /** Liga a câmera/tela. Só roda quando o responsável conecta. */
    private var pendingCapture: (() -> Unit)? = null

    private var session: MediaTransport.SessionConfig? = null
    private var lastSignalId = 0L
    private var signalLoop: Job? = null
    private var connectTimeout: Job? = null
    private var disconnectTimeout: Job? = null
    private var maxDuration: Job? = null

    @Volatile
    private var stopped = false

    /*
     * Concorrência: o estado acima só é mexido com o lock deste objeto (@Synchronized
     * / synchronized(this)). As callbacks do WebRTC rodam na thread de sinalização
     * dele, e os métodos do PeerConnection bloqueiam esperando essa mesma thread — por
     * isso as callbacks nunca pegam o lock direto: repassam o trabalho ao [scope],
     * senão travaria.
     */

    @Synchronized
    override fun start(session: MediaTransport.SessionConfig) {
        if (stopped) {
            Log.w(TAG, "Transporte já encerrado; ignorando start")
            return
        }
        // Um segundo start (novo check-in com um em andamento) substitui a sessão
        // anterior por inteiro, em vez de deixar a câmera e a conexão antigas abertas.
        if (this.session != null) {
            Log.i(TAG, "Nova sessão substitui a anterior")
            releaseConnection(sendBye = true)
        }
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
            SessionObserver(session.sessionId),
        ) ?: run {
            Log.e(TAG, "Falha ao criar PeerConnection")
            return
        }
        peerConnection = pc

        when (session.kind) {
            MediaTransport.Kind.CAMERA -> addCameraTracks(factory, pc)
            MediaTransport.Kind.SCREEN -> addScreenTrack(factory, pc, session)
        }

        createAndSendOffer(pc, session.sessionId)
        signalLoop = startSignalingLoop(session.sessionId)

        // Prazos: ninguém atendeu (painel fechado, comando velho que ficou na fila com
        // o aparelho sem rede) e teto de duração. Sem eles a sessão ficaria aberta.
        connectTimeout = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            Log.i(TAG, "Responsável não conectou a tempo; encerrando")
            stop()
        }
        maxDuration = scope.launch {
            delay(MAX_SESSION_MS)
            Log.i(TAG, "Tempo máximo da sessão atingido; encerrando")
            stop()
        }
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
        pendingCapture = { capturer.startCapture(1280, 720, 30) }
        pc.addTrack(factory.createVideoTrack("video", source), listOf(STREAM_ID))

        // O microfone só grava quando a conexão sobe (o WebRTC abre o áudio aí).
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
        var self: VideoCapturer? = null
        val capturer = ScreenCapturerAndroid(data, object : android.media.projection.MediaProjection.Callback() {
            override fun onStop() {
                // Só encerra se ainda for a captura atual: a de uma sessão substituída
                // também dispara isto ao ser liberada.
                if (self != null && videoCapturer === self) {
                    Log.i(TAG, "Projeção de tela encerrada pelo sistema")
                    stopAsync()
                }
            }
        })
        self = capturer
        videoCapturer = capturer
        val source = factory.createVideoSource(true)  // isScreencast
        videoSource = source
        surfaceHelper = SurfaceTextureHelper.create("ScreenThread", eglBase.eglBaseContext)
        capturer.initialize(surfaceHelper, appContext, source.capturerObserver)
        pendingCapture = { capturer.startCapture(1280, 720, 15) }
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

    private fun createAndSendOffer(pc: PeerConnection, sessionId: String) {
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                scope.launch {
                    client.postSignal(
                        sessionId, "offer",
                        JSONObject().put("type", "offer").put("sdp", sdp.description),
                    )
                }
            }
        }, MediaConstraints())
    }

    private fun startSignalingLoop(sessionId: String): Job = scope.launch {
        while (isActive) {
            client.fetchSignals(sessionId, lastSignalId).forEach { handleSignal(sessionId, it) }
            delay(POLL_MS)
        }
    }

    @Synchronized
    private fun handleSignal(sessionId: String, signal: SupabaseClient.Signal) {
        // A sessão pode ter sido encerrada ou trocada enquanto a leitura estava em curso.
        if (stopped || session?.sessionId != sessionId) return
        lastSignalId = maxOf(lastSignalId, signal.id)
        val pc = peerConnection ?: return
        try {
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
        } catch (e: JSONException) {
            // Sinal malformado: descarta só ele, a sessão segue.
            Log.w(TAG, "Sinal ${signal.kind} malformado ignorado", e)
        }
    }

    /** Observer de uma sessão; ignora eventos de uma conexão já substituída. */
    private inner class SessionObserver(private val sessionId: String) : SimplePcObserver() {

        override fun onIceCandidate(candidate: IceCandidate) {
            scope.launch {
                if (session?.sessionId != sessionId) return@launch
                client.postSignal(
                    sessionId, "candidate",
                    JSONObject()
                        .put("sdpMid", candidate.sdpMid)
                        .put("sdpMLineIndex", candidate.sdpMLineIndex)
                        .put("candidate", candidate.sdp),
                )
            }
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            scope.launch { onConnectionState(sessionId, state) }
        }
    }

    @Synchronized
    private fun onConnectionState(sessionId: String, state: PeerConnection.IceConnectionState?) {
        if (stopped || session?.sessionId != sessionId) return
        when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                connectTimeout?.cancel()
                disconnectTimeout?.cancel()
                // Só agora, com o responsável do outro lado, liga a câmera/tela.
                pendingCapture?.let {
                    pendingCapture = null
                    runCatching(it).onFailure { e -> Log.e(TAG, "Falha ao iniciar a captura", e) }
                }
            }
            // Queda momentânea (troca de rede): dá um tempo para voltar.
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                disconnectTimeout?.cancel()
                disconnectTimeout = scope.launch {
                    delay(DISCONNECT_GRACE_MS)
                    Log.i(TAG, "Conexão não voltou; encerrando")
                    stop()
                }
            }
            PeerConnection.IceConnectionState.FAILED,
            PeerConnection.IceConnectionState.CLOSED -> {
                Log.i(TAG, "Conexão $state; encerrando")
                stop()
            }
            else -> Unit
        }
    }

    /** Encerra fora da thread atual (callbacks do WebRTC ou da projeção). */
    private fun stopAsync() {
        Thread({ stop() }, "WebRtcStop").start()
    }

    /** Libera a conexão e a captura da sessão atual; o transporte segue utilizável. */
    private fun releaseConnection(sendBye: Boolean) {
        val sessionId = session?.sessionId.orEmpty()
        session = null
        // "bye" em thread próprio: o escopo pode ser cancelado logo depois e engoliria o envio.
        if (sendBye && sessionId.isNotEmpty()) {
            Thread { runCatching { client.postSignal(sessionId, "bye", JSONObject()) } }.start()
        }
        listOf(signalLoop, connectTimeout, disconnectTimeout, maxDuration).forEach { it?.cancel() }
        signalLoop = null
        connectTimeout = null
        disconnectTimeout = null
        maxDuration = null
        pendingCapture = null
        lastSignalId = 0L

        // Zera a referência antes de parar: o onStop da projeção compara com ela.
        val capturer = videoCapturer
        videoCapturer = null
        runCatching { capturer?.stopCapture() }
        capturer?.dispose()
        videoSource?.dispose()
        audioSource?.dispose()
        surfaceHelper?.dispose()
        peerConnection?.dispose()
        videoSource = null
        audioSource = null
        surfaceHelper = null
        peerConnection = null
    }

    @Synchronized
    override fun stop() {
        if (stopped) return
        stopped = true
        releaseConnection(sendBye = true)
        scope.cancel()
        eglBase.release()
        // Avisa o serviço para se encerrar (e a notificação sumir).
        runCatching { onClosed() }
    }

    companion object {
        private const val TAG = "WebRtcTransport"
        private const val STREAM_ID = "controle_infantil"
        private const val POLL_MS = 1500L

        /** Tempo para o responsável atender; depois disso a sessão é descartada. */
        private const val CONNECT_TIMEOUT_MS = 60_000L

        /** Quanto uma queda de conexão pode durar antes de encerrar. */
        private const val DISCONNECT_GRACE_MS = 15_000L

        /** Teto de uma sessão, mesmo conectada; para mais, o responsável inicia outra. */
        private const val MAX_SESSION_MS = 30 * 60_000L

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

/** Observer de PeerConnection com implementações vazias; as subclasses sobrescrevem o que usam. */
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
