package com.example.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

object SoundManager {
    private const val SAMPLE_RATE = 22050
    private var audioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var musicJob: Job? = null
    
    var musicVolume = 0.5f
    var sfxVolume = 0.8f
    
    // Simple state
    private var isPlayingMusic = false

    fun init() {
        synchronized(this) {
            if (audioTrack != null) return
            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(SAMPLE_RATE * 2) // 1 second buffer
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                audioTrack?.play()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private fun playPcm(samples: ShortArray) {
        synchronized(this) {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    try {
                        track.write(samples, 0, samples.size)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    
    fun playSwim() {
        scope.launch {
            val duration = 0.12f
            val numSamples = (SAMPLE_RATE * duration).toInt()
            val samples = ShortArray(numSamples)
            val volume = sfxVolume * 0.4f
            
            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE
                // frequency slides up: 200 Hz to 550 Hz
                val freq = 200f + (550f - 200f) * (t / duration)
                val angle = 2.0 * Math.PI * freq * t
                val value = (sin(angle) * 32767 * volume).toInt().coerceIn(-32768, 32767)
                samples[i] = value.toShort()
            }
            playPcm(samples)
        }
    }

    fun playCollect() {
        scope.launch {
            val duration = 0.20f
            val numSamples = (SAMPLE_RATE * duration).toInt()
            val samples = ShortArray(numSamples)
            val volume = sfxVolume * 0.5f
            
            val transitionSample = (numSamples * 0.35f).toInt()
            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE
                val freq = if (i < transitionSample) 784f else 1175f // G5 then D6
                val angle = 2.0 * Math.PI * freq * t
                val value = (sin(angle) * 32767 * volume).toInt().coerceIn(-32768, 32767)
                samples[i] = value.toShort()
            }
            playPcm(samples)
        }
    }

    fun playCrash() {
        scope.launch {
            val duration = 0.45f
            val numSamples = (SAMPLE_RATE * duration).toInt()
            val samples = ShortArray(numSamples)
            val volume = sfxVolume * 0.6f
            
            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE
                val freq = 350f - (350f - 80f) * (t / duration)
                val angle = 2.0 * Math.PI * freq * t
                
                val noise = Random.nextFloat() * 2f - 1f
                val signal = sin(angle) * 0.7f + noise * 0.3f
                val envelope = 1.0f - (t / duration)
                
                val value = (signal * 32767 * volume * envelope).toInt().coerceIn(-32768, 32767)
                samples[i] = value.toShort()
            }
            playPcm(samples)
        }
    }
    
    fun startMusic() {
        if (isPlayingMusic) return
        isPlayingMusic = true
        
        musicJob = scope.launch {
            val bpm = 110
            val beatMs = (60_000 / bpm).toLong()
            val scale = doubleArrayOf(
                130.81, 130.81, 196.00, 196.00, // C3, C3, G3, G3
                220.00, 220.00, 349.23, 349.23  // A3, A3, F3, F3
            )
            val bubbleMelody = doubleArrayOf(
                261.63, 329.63, 392.00, 523.25, // C4, E4, G4, C5
                440.00, 523.25, 587.33, 659.25  // A4, C5, D5, E5
            )
            
            var index = 0
            while (isPlayingMusic) {
                val bassFreq = scale[index % scale.size]
                val bubFreq = if (index % 4 == 0) bubbleMelody[(index / 2) % bubbleMelody.size] else 0.0
                
                val duration = beatMs / 1000f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                
                val vol = musicVolume * 0.22f
                
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val bassAngle = 2.0 * Math.PI * bassFreq * t
                    var signal = sin(bassAngle)
                    
                    if (bubFreq > 0) {
                        val bubAngle = 2.0 * Math.PI * bubFreq * t
                        val bubEnv = (1.0f - (t / (duration * 0.6f))).coerceIn(0f, 1f)
                        signal += sin(bubAngle) * 0.45f * bubEnv
                    }
                    
                    val finalEnv = if (t > duration * 0.82f) {
                        (1.0f - ((t - duration * 0.82f) / (duration * 0.18f))).coerceIn(0f, 1f)
                    } else 1.0f
                    
                    val value = (signal * 32767 * vol * finalEnv).toInt().coerceIn(-32768, 32767)
                    samples[i] = value.toShort()
                }
                playPcm(samples)
                index++
                delay(beatMs - 2)
            }
        }
    }
    
    fun stopMusic() {
        isPlayingMusic = false
        musicJob?.cancel()
        musicJob = null
    }

    fun release() {
        synchronized(this) {
            stopMusic()
            audioTrack?.let {
                try {
                    it.stop()
                    it.release()
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
            audioTrack = null
        }
    }
}
