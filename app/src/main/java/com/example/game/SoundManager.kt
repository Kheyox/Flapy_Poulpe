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

    fun init(context: android.content.Context) {
        synchronized(this) {
            if (audioTrack != null) return
            try {
                val builder = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
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
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    builder.setContext(context)
                }
                
                val track = builder.build()
                
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.play()
                    audioTrack = track
                } else {
                    track.release()
                    audioTrack = null
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                audioTrack = null
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
            val bpm = 92
            val beatMs = (60_000 / bpm).toLong()

            // Dreamy underwater progression: Am — F — C — G, four beats per chord.
            // Each chord = [bass, triad notes...] in Hz.
            val chords = arrayOf(
                doubleArrayOf(110.00, 220.00, 261.63, 329.63), // Am: A2 | A3 C4 E4
                doubleArrayOf(87.31, 174.61, 220.00, 261.63),  // F:  F2 | F3 A3 C4
                doubleArrayOf(130.81, 261.63, 329.63, 392.00), // C:  C3 | C4 E4 G4
                doubleArrayOf(98.00, 196.00, 246.94, 293.66)   // G:  G2 | G3 B3 D4
            )
            // Arpeggio pattern over the triad (indices into chord[1..3]), one note per beat
            val arpPattern = intArrayOf(0, 2, 1, 2)

            var beat = 0
            while (isPlayingMusic) {
                val chord = chords[(beat / 4) % chords.size]
                val bassFreq = chord[0]
                val melodyFreq = chord[1 + arpPattern[beat % 4]] * 2.0 // one octave up
                // A high sparkle every 8 beats adds magic without clutter
                val sparkleFreq = if (beat % 8 == 7) chord[2] * 4.0 else 0.0

                val duration = beatMs / 1000f
                val numSamples = (SAMPLE_RATE * duration).toInt()
                val samples = ShortArray(numSamples)
                val vol = musicVolume * 0.20f

                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE

                    // Layer 1 — round sine bass, gently decaying over the beat
                    val bassEnv = 1.0f - (t / duration) * 0.45f
                    var signal = sin(2.0 * Math.PI * bassFreq * t) * 0.50 * bassEnv

                    // Layer 2 — soft pad holding the chord triad
                    for (n in 1..3) {
                        signal += sin(2.0 * Math.PI * chord[n] * t) * 0.09
                    }

                    // Layer 3 — plucked arpeggio melody with a fast-decay envelope
                    val pluckEnv = (1.0f - (t / (duration * 0.75f))).coerceIn(0f, 1f)
                    signal += sin(2.0 * Math.PI * melodyFreq * t) * 0.32 * pluckEnv * pluckEnv

                    // Occasional shimmering sparkle on top
                    if (sparkleFreq > 0) {
                        val sparkEnv = (1.0f - (t / (duration * 0.5f))).coerceIn(0f, 1f)
                        signal += sin(2.0 * Math.PI * sparkleFreq * t) * 0.12 * sparkEnv
                    }

                    // Short attack/release ramps remove clicks at beat boundaries
                    val attack = (t / 0.012f).coerceAtMost(1f)
                    val release = if (t > duration * 0.85f) {
                        (1.0f - ((t - duration * 0.85f) / (duration * 0.15f))).coerceIn(0f, 1f)
                    } else 1.0f

                    val value = (signal * 32767 * vol * attack * release).toInt().coerceIn(-32768, 32767)
                    samples[i] = value.toShort()
                }
                playPcm(samples)
                beat++
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
