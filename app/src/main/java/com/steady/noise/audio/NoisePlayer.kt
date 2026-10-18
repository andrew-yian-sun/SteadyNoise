package com.steady.noise.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.steady.noise.NoiseType
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class NoisePlayer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { }
        .build()

    @Volatile
    private var active: Session? = null

    @Volatile
    private var track: AudioTrack? = null

    fun start(type: NoiseType, endElapsedRealtime: Long, onFinished: () -> Unit) {
        stop()
        val session = Session(onFinished)
        active = session
        Thread({
            try {
                if (!session.stop.get()) {
                    play(session, type, endElapsedRealtime)
                }
            } finally {
                if (active === session) {
                    audioManager.abandonAudioFocusRequest(focusRequest)
                    mainHandler.post(session.onFinished)
                }
            }
        }, "SteadyNoise").start()
    }

    fun stop() {
        active?.stop?.set(true)
        val current = track
        try {
            current?.pause()
            current?.flush()
        } catch (_: IllegalStateException) {
        }
    }

    private fun play(session: Session, type: NoiseType, endElapsedRealtime: Long) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val sampleRate = 48_000
        val channel = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channel, encoding)
        if (minBuffer <= 0) return

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(encoding)
            .setChannelMask(channel)
            .build()
        val bufferBytes = max(minBuffer, sampleRate * 2 / 5)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = audioTrack

        try {
            audioManager.requestAudioFocus(focusRequest)
            audioTrack.play()
            val generator = NoiseGenerator()
            val pcm = ShortArray(sampleRate / 20)
            while (!session.stop.get() && SystemClock.elapsedRealtime() < endElapsedRealtime) {
                generator.fill(pcm, type)
                var offset = 0
                while (offset < pcm.size && !session.stop.get()) {
                    val wrote = audioTrack.write(pcm, offset, pcm.size - offset)
                    if (wrote <= 0) return
                    offset += wrote
                }
            }
        } finally {
            if (track === audioTrack) {
                track = null
            }
            try {
                if (session.stop.get()) {
                    audioTrack.pause()
                    audioTrack.flush()
                }
                audioTrack.stop()
            } catch (_: IllegalStateException) {
            }
            audioTrack.release()
        }
    }

    private class Session(val onFinished: () -> Unit) {
        val stop = AtomicBoolean(false)
    }
}
