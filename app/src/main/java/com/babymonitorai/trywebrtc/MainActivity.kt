package com.babymonitorai.trywebrtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import org.webrtc.*

class MainActivity : AppCompatActivity() {

    companion object {
        private val premissions = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_NETWORK_STATE)
    }

    lateinit var peerConnectionFactory: PeerConnectionFactory
    var localPeer: PeerConnection? = null
    var remotePeer: PeerConnection? = null
    lateinit var localVideoTrack: VideoTrack
    lateinit var localAudioTrack: AudioTrack



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (!applicationContext.areAllPermissionsGranted(*premissions)) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.ACCESS_NETWORK_STATE), 1)
            }
        }
        btnStart.setOnClickListener { start() }
        btnCall.setOnClickListener{call()}
        btnHangup.setOnClickListener { hangup() }

        initVideos()

//        videoCapturer?.startCapture(960, 720, 30)

    }

    private fun initVideos() {
        val rootEglBase = EglBase.create()
        localViewRenderer.init(rootEglBase.eglBaseContext, null)
        remoteViewRenderer.init(rootEglBase.eglBaseContext, null)
        localViewRenderer.setZOrderMediaOverlay(true)
        remoteViewRenderer.setZOrderMediaOverlay(true)
    }

    private fun start() {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableVideoHwAcceleration(true)
                        .createInitializationOptions())
        peerConnectionFactory = PeerConnectionFactory(PeerConnectionFactory.Options())

        val videoCapturer: VideoCapturer? = createVideoCapturer()

        val audioConstraints = MediaConstraints()
        val videoConstraints = MediaConstraints()

        val videoSource = peerConnectionFactory.createVideoSource(videoCapturer)
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)

        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)
        localViewRenderer.visibility = View.VISIBLE

        localVideoTrack.addRenderer(VideoRenderer(localViewRenderer))
        videoCapturer?.startCapture(960, 720, 30)
    }

    private fun createVideoCapturer() =
            createCameraCapturer(Camera1Enumerator(false)) // TODO try camera2Enumerator


    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        val cameraName = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: deviceNames.firstOrNull { enumerator.isBackFacing(it) }
        return cameraName?.let { enumerator.createCapturer(it, CustomCameraEventsHandler()) }
    }


    private fun call() {
        val iceServers = mutableListOf<PeerConnection.IceServer>()

        val sdpConstraints = MediaConstraints()
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"))
        sdpConstraints.mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"))

        localPeer = peerConnectionFactory.createPeerConnection(
                iceServers,
                sdpConstraints,
                object : CustomPeerConnectionObserver("localPeerConnection") {
                    override fun onIceCandidate(iceCandidate: IceCandidate?) {
                        super.onIceCandidate(iceCandidate)
                        onIceCandidateReceived(localPeer, iceCandidate)
                    }
                })
        remotePeer = peerConnectionFactory.createPeerConnection(
                iceServers,
                sdpConstraints,
                object : CustomPeerConnectionObserver("remotePeerConnection") {
                    override fun onIceCandidate(iceCandidate: IceCandidate?) {
                        super.onIceCandidate(iceCandidate)
                        onIceCandidateReceived(remotePeer, iceCandidate)
                    }

                    override fun onAddStream(mediaStream: MediaStream?) {
                        super.onAddStream(mediaStream)
                        gotRemoteStream(mediaStream)
                    }
                })
        val stream = peerConnectionFactory.createLocalMediaStream("102")
        stream.addTrack(localAudioTrack)
        stream.addTrack(localVideoTrack)
        localPeer?.addStream(stream)

        localPeer?.createOffer(object : CustomSdpObserver("localCreateOffer") {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                super.onCreateSuccess(sessionDescription)
                localPeer?.setLocalDescription(CustomSdpObserver("localSetLocalDesc"), sessionDescription)
                remotePeer?.setRemoteDescription(CustomSdpObserver("remoteSetRemoteDesc"), sessionDescription)
                remotePeer?.createAnswer(object : CustomSdpObserver("remoteCreateOffer") {
                    override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                        super.onCreateSuccess(sessionDescription)
                        localPeer?.setRemoteDescription(CustomSdpObserver("localSetRemoteDesc"), sessionDescription)
                        remotePeer?.setLocalDescription(CustomSdpObserver("remoteSetLocalDesc"), sessionDescription)
                    }
                }, MediaConstraints())
            }
        }, sdpConstraints)
    }


    private fun gotRemoteStream(mediaStream: MediaStream?) {
        val videoTrack = mediaStream?.videoTracks?.first
        val audioTrack = mediaStream?.audioTracks?.first
        runOnUiThread {
            try {
                val remoteRenderer = VideoRenderer(remoteViewRenderer)
                remoteViewRenderer.visibility = View.VISIBLE
                videoTrack?.addRenderer(remoteRenderer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun onIceCandidateReceived(peer: PeerConnection?, iceCandidate: IceCandidate?) {
        if (peer == localPeer)
            remotePeer?.addIceCandidate(iceCandidate)
        else
            localPeer?.addIceCandidate(iceCandidate)
    }

    private fun hangup() {
        localPeer?.close()
        remotePeer?.close()
        localPeer = null
        remotePeer = null
    }


    fun Context.checkIsPermissionGranted(permission: String) =
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    fun Context.areAllPermissionsGranted(vararg permission: String) = permission.all { checkIsPermissionGranted(it) }

}
