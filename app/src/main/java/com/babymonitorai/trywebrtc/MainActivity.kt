package com.babymonitorai.trywebrtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import com.babymonitorai.trywebrtc.R.id.localViewRenderer
import org.webrtc.*

class MainActivity : AppCompatActivity() {

    companion object {
        private val premissions = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO)
    }

    lateinit var peerConnectionFactory: PeerConnectionFactory
    lateinit var localPeer: PeerConnection
    lateinit var remotePeer: PeerConnection


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (!applicationContext.areAllPermissionsGranted(*premissions)) {

            requestPermissions(arrayOf(Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO), 1)
        }

        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableVideoHwAcceleration(true)
                        .createInitializationOptions())
        peerConnectionFactory = PeerConnectionFactory(PeerConnectionFactory.Options())

        val videoCapturer: VideoCapturer? = createVideoCapturer()

        val audioConstraints = MediaConstraints()
        val videoConstraints = MediaConstraints()

        val videoSource = peerConnectionFactory.createVideoSource(videoCapturer)
        val localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)

        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        val localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)

        videoCapturer?.startCapture(960, 720, 30)

        localViewRenderer.setMirror(true)

        val rootEglBase = EglBase.create()
        localViewRenderer.init(rootEglBase.eglBaseContext, null)

        localVideoTrack.addRenderer(VideoRenderer(localViewRenderer))

    }

    private fun createVideoCapturer() =
            createCameraCapturer(Camera1Enumerator(false)) // TODO try camera2Enumerator


    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        val cameraName = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: deviceNames.firstOrNull { enumerator.isBackFacing(it) }
        return cameraName?.let { enumerator.createCapturer(it, null) }
    }

    fun Context.checkIsPermissionGranted(permission: String) =
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    fun Context.areAllPermissionsGranted(vararg permission: String) = permission.all { checkIsPermissionGranted(it) }

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
        object : CustomPeerConnectionObserver("remotePeerConnection"){
            override fun onIceCandidate(iceCandidate: IceCandidate?) {
                super.onIceCandidate(iceCandidate)
                onIceCandidateReceived(remotePeer,iceCandidate)
            }

            override fun onAddStream(mediaStream: MediaStream?) {
                super.onAddStream(mediaStream)
                gotRemoteStream(mediaStream)
            }
        })
    }

    private fun gotRemoteStream(mediaStream: MediaStream?) {

    }

    private fun onIceCandidateReceived(localPeer: PeerConnection, iceCandidate: IceCandidate?) {

    }

}
