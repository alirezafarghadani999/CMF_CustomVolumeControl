package alireza.f.cmfvolumecontrol

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnticipateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.SeekBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet.Constraint
import androidx.core.animation.doOnEnd
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.app.ServiceCompat.startForeground
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

// VolumeService.kt
class VolumeService : Service() {

    private lateinit var windowManager: WindowManager
    private var volumeView: View? = null
    private var VLC: View? = null

    private var isAnimating = false

    override fun onCreate() {
        super.onCreate()
        startForegroundService()

        // ساخت ویو شناور فقط یک بار
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (volumeView == null) {
            createVolumeOverlay()
        }
    }

    @SuppressLint("InflateParams")
    private fun createVolumeOverlay() {
        volumeView = LayoutInflater.from(this).inflate(R.layout.volume_overlay, null)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }

        volumeView!!.visibility = View.GONE
        volumeView!!.alpha = 0f

        try {
            windowManager.addView(volumeView, layoutParams)
            setupVolumeControls()
        } catch (e: Exception) {
            Log.e("VolumeService", "Error adding overlay view", e)
        }
    }


    fun vibrate(context: Context, duration: Long = 200) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun setupVolumeControls() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val seekBar = volumeView!!.findViewById<SeekBar>(R.id.volumeSeekBar)
        val vlc = volumeView!!.findViewById<ConstraintLayout>(R.id.vlc)

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)


        seekBar.max = maxVolume
        seekBar.progress = currentVolume

        val animator = ValueAnimator.ofFloat(1f, 1.4f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                vlc.scaleX = scale
                vlc.scaleY = scale
                vlc.translationX = (scale-1f)*60
            }
        }

        val animator_scaleY = ValueAnimator.ofFloat(1f, 1.9f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                vlc.scaleY = scale
            }
        }

        val max_anim = ValueAnimator.ofFloat(1f, 1.9f).apply {
            duration = 300
            interpolator = AnticipateInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                seekBar!!.translationX = -(scale-1f)*20
                }
        }

        val mm_anim_scale = ValueAnimator.ofFloat(1f, 0.7f).apply {
            duration = 300
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                seekBar!!.scaleY = scale
            }
        }

        val min_anim = ValueAnimator.ofFloat(1f, 1.9f).apply {
            duration = 300
            interpolator = AnticipateInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                seekBar!!.translationX = (scale-1f)*20
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                }

                if (progress == seekBar!!.max){
                    val max_anim_reverse = max_anim.clone().apply { reverse() }
                    val mm_anim_scale_reverse = mm_anim_scale.clone().apply { reverse() }

                    AnimatorSet().apply {
                        playSequentially(mm_anim_scale, mm_anim_scale_reverse)
                        playSequentially(max_anim, max_anim_reverse)
                        vibrate(seekBar.context,20)
                    }

                }

                if (progress == 0){
                    val min_anim_reverse = min_anim.clone().apply { reverse() }
                    val mm_anim_scale_reverse = mm_anim_scale.clone().apply { reverse() }

                    AnimatorSet().apply {
                        playSequentially(mm_anim_scale, mm_anim_scale_reverse)
                        playSequentially(min_anim, min_anim_reverse)
                        vibrate(seekBar.context,20)

                    }

                }
            }
            @SuppressLint("SuspiciousIndentation")
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                hideHandler.removeCallbacks(hideRunnable)
                if (!animator.isStarted)
                    animator.start()
                    animator_scaleY.start()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                resetHideTimer()
                animator.reverse()
                animator_scaleY.reverse()
            }
        })
    }

    private fun startForegroundService() {
        val channelId = "volume_service_channel"
        val channelName = "Volume Service"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Volume Control Active")
            .setContentText("Tap to open settings")
            .build()

        startForeground(1, notification)
    }

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        animateOut()
    }

    fun showOverlay() {
        updateVolumeDisplay()
        if (volumeView?.visibility != View.VISIBLE) {
            animateIn()
        }
        resetHideTimer()
    }

    private fun animateIn() {
        if (isAnimating) return
        isAnimating = true

        volumeView?.apply {
            visibility = View.VISIBLE
            alpha = 0f
            translationX = -200f

            animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    isAnimating = false
                }
                .start()
        }
    }

    private fun animateOut() {
        if (isAnimating) return
        isAnimating = true

        volumeView?.apply {
            animate()
                .alpha(0f)
                .translationX(-200f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    visibility = View.GONE
                    isAnimating = false
                }
                .start()
        }
    }

    fun updateVolumeDisplay(specificVolume: Int? = null) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val seekBar = volumeView?.findViewById<SeekBar>(R.id.volumeSeekBar)

        if (seekBar == null) {
            createVolumeOverlay()
        }

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = specificVolume ?: audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        volumeView?.findViewById<SeekBar>(R.id.volumeSeekBar)?.apply {
            max = maxVolume
            progress = currentVolume
        }
    }


    private fun resetHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 2000)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SHOW_VOLUME_PANEL" -> {
                showOverlay()
            }
            "UPDATE_VOLUME_PANEL" -> {
                val volumeLevel = intent.getIntExtra("VOLUME_LEVEL", -1)
                if (volumeLevel >= 0) {
                    updateVolumeDisplay(volumeLevel)
                    if (volumeView?.visibility != View.VISIBLE) {
                        showOverlay()
                    } else {
                        resetHideTimer()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (volumeView != null) {
                windowManager.removeView(volumeView)
                volumeView = null
            }
        } catch (e: Exception) {
            Log.e("VolumeService", "Error in onDestroy", e)
        }
    }
}

class VolumeReceiver(private val service: VolumeService) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
            service.showOverlay()
        }
    }
}