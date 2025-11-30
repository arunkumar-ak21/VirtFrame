package com.example.remotedisplay

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.*

class StreamActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceViewRenderer
    
    private lateinit var rootEglBase: EglBase
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var peerConnection: PeerConnection
    
    private lateinit var signalingClient: SignalingClient
    private lateinit var touchInputSender: TouchInputSender

    private var videoCapturer: VideoCapturer? = null
    private var localVideoTrack: VideoTrack? = null
    
    // Touch handling variables
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var downX = 0f
    private var downY = 0f
    private var isScrolling = false
    private var isDragging = false
    private val MOVE_THRESHOLD = 10f // Pixels
    
    // Double Tap Logic
    private var lastTapTime: Long = 0
    private var lastTapX = 0f
    private var lastTapY = 0f
    private val DOUBLE_TAP_TIMEOUT = 300L // ms
    private val DOUBLE_TAP_DISTANCE = 100f // pixels

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Full screen immersive mode
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        
        setContentView(R.layout.activity_stream)
        supportActionBar?.hide()

        rootEglBase = EglBase.create()
        surfaceView = findViewById(R.id.surfaceView)

        val ip = intent.getStringExtra("IP_ADDRESS") ?: return
        val cameraMode = intent.getBooleanExtra("CAMERA_MODE", false)

        adjustSurfaceViewSize()

        initWebRTC()
        startStream(ip, cameraMode)

        surfaceView.setOnTouchListener { v, event ->
            handleTouch(v, event)
            true
        }
    }

    private fun adjustSurfaceViewSize() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Target Aspect Ratio 16:9
        val targetAspect = 16f / 9f
        
        var newWidth = screenWidth
        var newHeight = (screenWidth / targetAspect).toInt()

        if (newHeight > screenHeight) {
            newHeight = screenHeight
            newWidth = (screenHeight * targetAspect).toInt()
        }

        val params = surfaceView.layoutParams
        params.width = newWidth
        params.height = newHeight
        surfaceView.layoutParams = params
    }

    private fun initWebRTC() {
        val options = PeerConnectionFactory.InitializationOptions.builder(this)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val videoEncoderFactory = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
        val videoDecoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
            .createPeerConnectionFactory()
            
        surfaceView.init(rootEglBase.eglBaseContext, null)
        surfaceView.setEnableHardwareScaler(false)
        surfaceView.setMirror(false)
        surfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
    }
    
    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        // Try to find back facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        // Front facing
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }

    private fun startStream(ip: String, cameraMode: Boolean) {
        val serverUrl = "http://$ip:8080"
        signalingClient = SignalingClient(serverUrl)
        touchInputSender = TouchInputSender(ip, 9999)

        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
        
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(p0: IceCandidate?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                if (stream?.videoTracks?.isNotEmpty() == true) {
                    val videoTrack = stream.videoTracks[0]
                    runOnUiThread {
                        videoTrack.addSink(surfaceView)
                        surfaceView.requestLayout()
                    }
                }
            }
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })!!
        
        if (cameraMode) {
            val enumerator = Camera2Enumerator(this)
            videoCapturer = createCameraCapturer(enumerator)
            
            if (videoCapturer != null) {
                val videoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
                videoCapturer!!.initialize(SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext), this, videoSource.capturerObserver)
                videoCapturer!!.startCapture(1280, 720, 30)
                
                localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)
                peerConnection.addTrack(localVideoTrack, listOf("stream1"))
            }
        }

        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))

        peerConnection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            sendOfferToServer(desc)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, it)
                }
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, mediaConstraints)
        
        // Start Touch Sender
        CoroutineScope(Dispatchers.IO).launch {
            touchInputSender.connect()
        }
    }

    private fun sendOfferToServer(sdp: SessionDescription) {
        CoroutineScope(Dispatchers.IO).launch {
            val answer = signalingClient.sendOffer(sdp)
            if (answer != null) {
                peerConnection.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, answer)
            } else {
                runOnUiThread {
                    Toast.makeText(this@StreamActivity, "Failed to connect", Toast.LENGTH_SHORT).show()
                    finish() // Close activity on failure
                }
            }
        }
    }

    private fun handleTouch(v: View, event: MotionEvent) {
        val viewWidth = v.width.toFloat()
        val viewHeight = v.height.toFloat()
        
        // 1. Simplified Coordinate Mapping (View is forced to 16:9)
        var normX = event.x / viewWidth
        var normY = event.y / viewHeight
        
        // Clamp
        normX = normX.coerceIn(0f, 1f)
        normY = normY.coerceIn(0f, 1f)
        
        // 2. Scroll Handling (2 fingers)
        if (event.pointerCount == 2) {
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    isScrolling = true
                    lastTouchX = event.getX(0)
                    lastTouchY = event.getY(0)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isScrolling) {
                        val currentX = event.getX(0)
                        val currentY = event.getY(0)
                        val dx = currentX - lastTouchX
                        val dy = currentY - lastTouchY
                        
                        // Send Scroll (Adjust sensitivity as needed)
                        touchInputSender.sendScroll(dx * 0.8f, dy * 0.8f)
                        
                        lastTouchX = currentX
                        lastTouchY = currentY
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    isScrolling = false
                }
            }
            return
        }
        
        // 3. Mouse Handling (1 finger)
        if (!isScrolling) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    isDragging = false
                    
                    touchInputSender.sendDown(normX, normY)
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = Math.abs(event.x - downX)
                    val deltaY = Math.abs(event.y - downY)
                    
                    if (isDragging || deltaX > MOVE_THRESHOLD || deltaY > MOVE_THRESHOLD) {
                        isDragging = true
                        touchInputSender.sendMove(normX, normY)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val currentTime = System.currentTimeMillis()
                    
                    if (isDragging) {
                        touchInputSender.sendUp(normX, normY)
                    } else {
                        // Tap Logic
                        val timeDiff = currentTime - lastTapTime
                        val distDiff = Math.hypot((event.x - lastTapX).toDouble(), (event.y - lastTapY).toDouble()).toFloat()
                        
                        if (timeDiff < DOUBLE_TAP_TIMEOUT && distDiff < DOUBLE_TAP_DISTANCE) {
                            // Double Tap
                            val lastNormX = (lastTapX / viewWidth).coerceIn(0f, 1f)
                            val lastNormY = (lastTapY / viewHeight).coerceIn(0f, 1f)
                            
                            touchInputSender.sendDoubleClick(lastNormX, lastNormY)
                            lastTapTime = 0 
                        } else {
                            // Single Tap
                            touchInputSender.sendClick(normX, normY)
                            
                            lastTapTime = currentTime
                            lastTapX = event.x
                            lastTapY = event.y
                        }
                        touchInputSender.sendUp(normX, normY)
                    }
                    isDragging = false
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::touchInputSender.isInitialized) touchInputSender.close()
        if (::peerConnection.isInitialized) peerConnection.close()
        if (::peerConnectionFactory.isInitialized) peerConnectionFactory.dispose()
        if (::surfaceView.isInitialized) surfaceView.release()
        if (::rootEglBase.isInitialized) rootEglBase.release()
    }
}
