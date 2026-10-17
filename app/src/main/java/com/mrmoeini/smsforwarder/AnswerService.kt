package com.mrmoeini.smsforwarder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * Keeps the app alive (foreground service) so it can react to incoming calls: decides which rule
 * matches, answers the call, plays the message through the speaker, then hangs up.
 */
class AnswerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var ringing = false
    private var evaluated = false
    private var answering = false
    private var ringStart = 0L
    private var currentNumber = ""
    private var player: MediaPlayer? = null
    private var savedVoiceVolume = -1
    private var answerRunnable: Runnable? = null
    private val evalRunnable = Runnable { evaluate() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.ca_master), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, CallAnswerActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(getString(R.string.ca_master))
            .setContentText(getString(R.string.state_on))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, n)
        RuleStore.addLog(this, "AUTO-ANSWER: service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        stopPlayback()
        restoreAudio()
        RuleStore.addLog(this, "AUTO-ANSWER: service stopped")
        super.onDestroy()
    }

    /** Called by [CallStateReceiver] on the main thread. */
    fun onPhoneState(state: String, number: String) {
        handler.post { handleState(state, number) }
    }

    private fun handleState(state: String, number: String) {
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!ringing) {
                    ringing = true
                    evaluated = false
                    ringStart = SystemClock.elapsedRealtime()
                    currentNumber = ""
                }
                if (number.isNotEmpty()) currentNumber = number
                handler.removeCallbacks(evalRunnable)
                if (!evaluated) handler.postDelayed(evalRunnable, 700)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // The user picked up by hand: do not interfere. Our own answer sets [answering].
                if (!answering) cancelPending()
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                cancelPending()
                stopPlayback()
                restoreAudio()
                ringing = false
                evaluated = false
                answering = false
            }
        }
    }

    private fun cancelPending() {
        handler.removeCallbacks(evalRunnable)
        answerRunnable?.let { handler.removeCallbacks(it) }
        answerRunnable = null
    }

    private fun evaluate() {
        if (!ringing || evaluated) return
        evaluated = true
        val who = if (currentNumber.isEmpty()) "unknown number" else currentNumber
        val rule = AnswerRuleStore.load(this).firstOrNull { it.matches(currentNumber, Calendar.getInstance()) }
        if (rule == null) {
            RuleStore.addLog(this, "CALL from $who: no matching answer rule")
            return
        }
        val elapsed = SystemClock.elapsedRealtime() - ringStart
        val wait = (rule.delaySec * 1000L - elapsed).coerceAtLeast(0L)
        RuleStore.addLog(this, "CALL from $who: matched rule, answering in ${wait / 1000}s")
        val r = Runnable { answer(rule, who) }
        answerRunnable = r
        handler.postDelayed(r, wait)
    }

    private fun answer(rule: AnswerRule, who: String) {
        answerRunnable = null
        if (!ringing) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            RuleStore.addLog(this, "CALL from $who: ANSWER_PHONE_CALLS permission is not granted")
            return
        }
        try {
            answering = true
            getSystemService(TelecomManager::class.java).acceptRingingCall()
            RuleStore.addLog(this, "CALL from $who: answered")
            handler.postDelayed({ playMessage(rule) }, 1500)
        } catch (e: Exception) {
            answering = false
            RuleStore.addLog(this, "CALL from $who: answer failed ${e.javaClass.simpleName}")
        }
    }

    private fun playMessage(rule: AnswerRule) {
        if (!answering) return
        val file = AnswerRuleStore.audioFile(this, rule.audioFile)
        if (!file.exists()) {
            RuleStore.addLog(this, "AUTO-ANSWER: message file is missing")
            hangUp()
            return
        }
        val am = getSystemService(AudioManager::class.java)
        try {
            savedVoiceVolume = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            am.setStreamVolume(
                AudioManager.STREAM_VOICE_CALL,
                am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0
            )
        } catch (_: Exception) {
        }
        enableSpeaker(am)
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                RuleStore.addLog(this, "AUTO-ANSWER: message finished")
                handler.postDelayed({ hangUp() }, 800)
            }
            mp.setOnErrorListener { _, what, extra ->
                RuleStore.addLog(this, "AUTO-ANSWER: playback error $what/$extra")
                hangUp()
                true
            }
            mp.prepare()
            mp.start()
            player = mp
            RuleStore.addLog(this, "AUTO-ANSWER: playing message")
        } catch (e: Exception) {
            RuleStore.addLog(this, "AUTO-ANSWER: playback failed ${e.javaClass.simpleName}")
            hangUp()
        }
    }

    private fun hangUp() {
        stopPlayback()
        restoreAudio()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getSystemService(TelecomManager::class.java).endCall()
            }
        } catch (e: Exception) {
            RuleStore.addLog(this, "AUTO-ANSWER: hang up failed ${e.javaClass.simpleName}")
        }
        answering = false
    }

    private fun stopPlayback() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }

    @Suppress("DEPRECATION")
    private fun enableSpeaker(am: AudioManager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val dev = am.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (dev != null) am.setCommunicationDevice(dev)
            }
            am.isSpeakerphoneOn = true
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreAudio() {
        try {
            val am = getSystemService(AudioManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.clearCommunicationDevice()
            am.isSpeakerphoneOn = false
            if (savedVoiceVolume >= 0) {
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, savedVoiceVolume, 0)
                savedVoiceVolume = -1
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val CHANNEL = "answer_service"
        private const val NOTIF_ID = 7001

        @Volatile
        var instance: AnswerService? = null

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AnswerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AnswerService::class.java))
        }
    }
}
