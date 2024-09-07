package io.itch.mattemade.neonghost.tempo

import com.littlekt.Context
import com.littlekt.audio.AudioClipEx
import com.littlekt.util.seconds
import io.itch.mattemade.neonghost.StreamBpm
import kotlin.time.Duration

class Choreographer(private val context: Context) {

    var masterVolume = 1f
        set(value) {
            currentlyPlayingTrack?.let {
                it.stream.setVolume(currentlyPlayingId, it.basicVolume * value)
            }
            field = value
        }
    private var secondsPerBeat = 0f
    private var doubleSecondsPerBeat = 0f
    private var secondsPerMeasure = 0f
    var playbackRateBasedDt: Duration = Duration.ZERO
    var bpmBasedDt: Duration = Duration.ZERO
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
    private val everActiveSoundClips = mutableSetOf<AudioClipEx>()
    private var currentlyPlayingId: Int = 0
    val isActive: Boolean
        get() = currentlyPlaying != null

    fun play(music: StreamBpm) {
        if (music === currentlyPlayingTrack) {
            return
        }
        currentlyPlaying?.stop(currentlyPlayingId)
        currentlyPlayingId = music.stream.play(volume = music.basicVolume * masterVolume, referenceDistance = 10000f, rolloffFactor = 0f, loop = true)
        music.stream.setPlaybackRate(currentlyPlayingId, playbackRate.toFloat())
        bpm = music.bpm
        currentlyPlaying = music.stream
        time = music.offset
        secondsPerBeat = 60f / music.bpm
        doubleSecondsPerBeat = secondsPerBeat * 2f
        secondsPerMeasure = secondsPerBeat * 4
        currentlyPlayingTrack = music
    }

    var xPosition: Float = 0f
    var yPosition: Float = 0f
    fun updatePosition(x: Float, y: Float) {
        xPosition = x
        yPosition = y
        currentlyPlaying?.setPosition(currentlyPlayingId, x, y)
    }

    fun setPlaybackRate(rate: Float) {
        playbackRate = rate.toDouble()
        currentlyPlaying?.setPlaybackRate(currentlyPlayingId, rate)
        everActiveSoundClips.forEach {
            it.setPlaybackRateAll(rate)
        }
    }

    fun update(dt: Duration) {
        playbackRateBasedDt = dt.times(playbackRate)
        time += playbackRateBasedDt.seconds
        toBeat = (time % secondsPerBeat) / secondsPerBeat
        toMeasure = (time % secondsPerMeasure) / secondsPerMeasure
        bpmBasedDt = playbackRateBasedDt//if (bpm > 0f) playbackRateBasedDt.times(bpm / 150.0) else playbackRateBasedDt
    }
    fun uiSound(sound: AudioClipEx, volume: Float, onEnd: ((Int) -> Unit)? = null): Int {
        everActiveSoundClips.add(sound)
        val id = sound.play(positionX = xPosition, positionY = yPosition, volume = volume * masterVolume, onEnded = onEnd)
        sound.setPlaybackRate(id, playbackRate.toFloat())
        return id
    }

    fun sound(sound: AudioClipEx, x: Float, y: Float, looping: Boolean = false): Int {
        everActiveSoundClips.add(sound)
        val id = sound.play(positionX = x, positionY = y, volume = masterVolume, loop = looping)
        sound.setPlaybackRate(id, playbackRate.toFloat())
        return id
    }

    fun soundIgnoringPlaybackRate(sound: AudioClipEx, x: Float, y: Float, looping: Boolean = false): Int {
        val id = sound.play(positionX = x, positionY = y, volume = masterVolume, loop = looping)
        return id
    }
}