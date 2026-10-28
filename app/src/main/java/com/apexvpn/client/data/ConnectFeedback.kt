package com.apexvpn.client.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * «Ощущения»: звук и вибрация при подключении VPN.
 * Звуки синтезируются на лету — без файлов в APK. На беззвучном телефон молчит.
 */
object ConnectFeedback {
    data class Sound(val id: String, val title: String, val subtitle: String)

    val sounds = listOf(
        Sound("signal", "Сигнал", "Два коротких тона"),
        Sound("crystal", "Кристалл", "Звонкий и чистый"),
        Sound("sphere", "Сфера", "Мягкий низкий"),
        Sound("chime", "Звон", "Три ноты вверх"),
    )

    fun title(id: String) = sounds.firstOrNull { it.id == id }?.title ?: sounds[0].title

    /** Вызывается при переходе в «Защищено». */
    fun onConnected(context: Context) {
        val s = AppSettings.value
        if (s.vibration) vibrate(context)
        if (s.connectSound) play(context, s.sound)
    }

    fun vibrate(context: Context) = runCatching {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!v.hasVibrator()) return@runCatching
        if (Build.VERSION.SDK_INT >= 29) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        } else if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 70, 40), -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 30, 70, 40), -1)
        }
    }

    /** [force] — проба звука в настройках (громкость всё равно системная). */
    fun play(context: Context, id: String, force: Boolean = false) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (!force && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        Thread {
            runCatching {
                val pcm = synth(id)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(pcm.size * 1000L / RATE + 150)
                track.release()
            }
        }.apply { isDaemon = true }.start()
    }

    private const val RATE = 44100

    /** Ноты: частота, начало и длина в секундах, затухание. */
    private class Note(val f: Double, val start: Double, val len: Double, val decay: Double, val harm: Double = 0.0)

    private fun synth(id: String): ShortArray {
        val notes = when (id) {
            "crystal" -> listOf(Note(1760.0, 0.0, 0.9, 5.0, 0.35), Note(2637.0, 0.06, 0.8, 6.0, 0.2))
            "sphere" -> listOf(Note(330.0, 0.0, 0.8, 3.5, 0.15), Note(494.0, 0.12, 0.7, 4.0, 0.1))
            "chime" -> listOf(Note(1047.0, 0.0, 0.5, 6.0, 0.25), Note(1319.0, 0.12, 0.5, 6.0, 0.25), Note(1568.0, 0.24, 0.7, 5.0, 0.25))
            else -> listOf(Note(880.0, 0.0, 0.12, 14.0), Note(1320.0, 0.16, 0.2, 10.0)) // signal
        }
        val total = notes.maxOf { it.start + it.len }
        val out = DoubleArray((total * RATE).toInt() + 1)
        for (n in notes) {
            val s0 = (n.start * RATE).toInt()
            val count = (n.len * RATE).toInt()
            for (i in 0 until count) {
                val t = i.toDouble() / RATE
                val attack = minOf(1.0, t / 0.006)
                val env = attack * exp(-n.decay * t)
                val w = sin(2 * PI * n.f * t) + n.harm * sin(2 * PI * n.f * 2.01 * t)
                val k = s0 + i
                if (k < out.size) out[k] += w * env
            }
        }
        val peak = out.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6)
        return ShortArray(out.size) { (out[it] / peak * 0.6 * Short.MAX_VALUE).toInt().toShort() }
    }
}
