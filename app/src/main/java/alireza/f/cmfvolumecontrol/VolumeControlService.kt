package alireza.f.cmfvolumecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class VolumeControlService : AccessibilityService() {
    private lateinit var audioManager: AudioManager

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("VolumeControlService", "Service connected")

        val info = AccessibilityServiceInfo()
        info.flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        Log.d("VolumeControlService", "KeyEvent received: ${event.keyCode}")

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    adjustVolume(true)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    adjustVolume(false)
                    return true
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
            AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
        )

        Log.d("VolumeControlService", "Volume changed to: $newVolume")
    }
}