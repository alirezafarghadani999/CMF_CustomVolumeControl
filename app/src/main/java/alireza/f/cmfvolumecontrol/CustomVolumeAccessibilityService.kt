package alireza.f.cmfvolumecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class CustomVolumeAccessibilityService : AccessibilityService() {
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var isKeyPressed = false
    private var currentKeyCode = 0

    private val volumeChangeRunnable = object : Runnable {
        override fun run() {
            if (isKeyPressed) {
                when (currentKeyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> adjustVolume(true)
                    KeyEvent.KEYCODE_VOLUME_DOWN -> adjustVolume(false)
                }
                handler.postDelayed(this, 150)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo()
        info.flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        serviceInfo = info

        Log.d("CustomVolumeService", "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d("CustomVolumeService", "KeyEvent received: ${event.keyCode}, action: ${event.action}")

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (!isKeyPressed) {
                            // اولین فشار دکمه
                            isKeyPressed = true
                            currentKeyCode = event.keyCode

                            if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                                adjustVolume(true)
                            } else {
                                adjustVolume(false)
                            }

                            val intent = Intent(this, VolumeService::class.java)
                            intent.action = "SHOW_VOLUME_PANEL"
                            startService(intent)

                            handler.postDelayed(volumeChangeRunnable, 500)
                        }
                        return true
                    }
                    KeyEvent.ACTION_UP -> {
                        isKeyPressed = false
                        handler.removeCallbacks(volumeChangeRunnable)
                        return true
                    }
                }
            }
        }
        return super.onKeyEvent(event)
    }

    private fun adjustVolume(increase: Boolean) {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVolume = if (increase) {
            (currentVolume + 1).coerceAtMost(maxVolume)
        } else {
            (currentVolume - 1).coerceAtLeast(0)
        }

        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            newVolume,
            0
        )

        val intent = Intent(this, VolumeService::class.java)
        intent.action = "UPDATE_VOLUME_PANEL"
        intent.putExtra("VOLUME_LEVEL", newVolume)
        startService(intent)

        Log.d("CustomVolumeService", "Volume adjusted to: $newVolume")
    }
}