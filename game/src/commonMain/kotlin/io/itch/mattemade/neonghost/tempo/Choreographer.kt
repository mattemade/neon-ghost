package io.itch.mattemade.neonghost.tempo

import com.littlekt.Context
import com.littlekt.audio.AudioClipEx
import com.littlekt.audio.AudioStreamEx
import com.littlekt.util.seconds
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.StreamBpm
import kotlin.time.Duration

class Choreographer(private val context: Context) {

    private var secondsPerBeat = 0f
    private var doubleSecondsPerBeat = 0f
    private var secondsPerMeasure = 0f
    var bpm = 0f
        private set
    var time = 0f
        private set
    var toBeat = 0f
        private set
    var toMeasure = 0f
        private set

    private var currentlyPlaying: AudioClipEx? = null
    private var currentlyPlayingId: Int = 0
    val isActive: Boolean
        get() = currentlyPlaying != null

    fun play(music: StreamBpm) {
        currentlyPlaying?.stop(currentlyPlayingId)
        currentlyPlayingId = music.stream.play(volume = 0.1f, referenceDistance = 10000f, loop = true)
        currentlyPlaying = music.stream
        time = music.offset
        secondsPerBeat = 60f / music.bpm
        doubleSecondsPerBeat = secondsPerBeat * 2f
        secondsPerMeasure = secondsPerBeat * 4
    }

    fun update(dt: Duration) {
        time += dt.seconds
        toBeat = (time % secondsPerBeat) / secondsPerBeat
        toMeasure = (time % secondsPerMeasure) / secondsPerMeasure
    }
}