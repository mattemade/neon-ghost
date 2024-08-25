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
    var adjustedDt: Duration = Duration.ZERO
    var bpm = 0f
        private set
    var time = 0f
        private set
    var toBeat = 0f
        private set
    var toMeasure = 0f
        private set

    var playbackRate = 1.0
    var currentlyPlayingTrack: StreamBpm? = null
        private set
    private var currentlyPlaying: AudioClipEx? = null
    private var currentlyPlayingId: Int = 0
    val isActive: Boolean
        get() = currentlyPlaying != null

    fun play(music: StreamBpm) {
        currentlyPlaying?.stop(currentlyPlayingId)
        currentlyPlayingId = music.stream.play(volume = 0.1f, referenceDistance = 10000f, loop = true)
        music.stream.setPlaybackRate(currentlyPlayingId, playbackRate.toFloat())
        bpm = music.bpm
        currentlyPlaying = music.stream
        time = music.offset
        secondsPerBeat = 60f / music.bpm
        doubleSecondsPerBeat = secondsPerBeat * 2f
        secondsPerMeasure = secondsPerBeat * 4
        currentlyPlayingTrack = music
    }

    fun setPlaybackRate(rate: Float) {
        playbackRate = rate.toDouble()
        currentlyPlaying?.setPlaybackRate(currentlyPlayingId, rate)
    }

    fun update(dt: Duration) {
        adjustedDt = dt.times(playbackRate)
        time += adjustedDt.seconds
        toBeat = (time % secondsPerBeat) / secondsPerBeat
        toMeasure = (time % secondsPerMeasure) / secondsPerMeasure
    }
}