package com.babymonitorai.trywebrtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.webrtc.*

class MainActivity : AppCompatActivity() {

    companion object {
        private val premissions = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO)
    }

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
        val peerConnectionFactory = PeerConnectionFactory(PeerConnectionFactory.Options())

        val videoCapturer: VideoCapturer? = createVideoCapturer()

        val mediaConstraints = MediaConstraints()

        val videoSource = peerConnectionFactory.createVideoSource(videoCapturer)
        val videoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)

        val audioSource = peerConnectionFactory.createAudioSource(mediaConstraints)
        val audioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)

        videoCapturer?.startCapture(960, 720, 30)

        surfaceViewRenderer.setMirror(true)

        val rootEglBase = EglBase.create()
        surfaceViewRenderer.init(rootEglBase.eglBaseContext, null)

        videoTrack.addRenderer(VideoRenderer(surfaceViewRenderer))

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
}
