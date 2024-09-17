package io.itch.mattemade.neonghost.tempo

import com.littlekt.Context
import com.littlekt.audio.AudioClipEx
import com.littlekt.util.seconds
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.StreamBpm
import kotlin.time.Duration

class Choreographer(private val context: Context) {

    var masterVolume = 1f
        set(value) {
            currentlyPlayingTrack?.let {
                it.stream.setVolume(currentlyPlayingId, it.basicVolume * value * uiMusicVolume)
            }
            field = value
        }
    var targetUiMusicVolume = 1f
    var lastUiMusicVolume = 1f
    var uiMusicVolumeChangeIn = 0f
    var uiMusicVolume = targetUiMusicVolume
        set(value) {
            currentlyPlayingTrack?.let {
                val volume = it.basicVolume * value * masterVolume
                it.stream.setVolume(currentlyPlayingId, volume)
            }
            field = value
        }
    private var secondsPerBeat = 0f
    private var doubleSecondsPerBeat = 0f
    private var secondsPerMeasure = 0f
    private var holdTrack = false
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

    var startNextTrackWithoutFading: Boolean = false
    var playbackRate = 1.0
    var currentlyPlayingTrack: StreamBpm? = null
        private set
    private var currentlyPlaying: AudioClipEx? = null
    private val everActiveSoundClips = mutableSetOf<AudioClipEx>()
    private var currentlyPlayingId: Int = 0
    val isActive: Boolean
        get() = currentlyPlaying != null

    private var isFullyStopped = false

    fun reset() {
        isFullyStopped = false
        holdTrack = false
    }

    fun fullStop() {
        isFullyStopped = true
        toBeat = 0.4f // to make enemies move linearly
        toMeasure = 1f // to make Rei always hit the beat
    }

    var previousMusic: String? = null
    var currentMusic: String? = null
    var nextTrackAfterFade: StreamBpm? = null
    val fadeOutNotAllowedWhenTurningOn = setOf("magical girl optimistic", "magical girl 3d", "bassy_beat")
    fun play(music: StreamBpm/*, allowFadeOut: Boolean = false*/) {
        if (isFullyStopped) {
            return
        }
        if (holdTrack && music.name != "magical girl optimistic") {
            return
        }
        if (music.name == "magical girl optimistic") {
            holdTrack = true
        }
        previousMusic = currentMusic
        currentMusic = music.name
        if (music === currentlyPlayingTrack) {
            return
        }
        val allowFadeOut = !fadeOutNotAllowedWhenTurningOn.contains(music.name) && !startNextTrackWithoutFading
        startNextTrackWithoutFading = false
        if (allowFadeOut) {
            nextTrackAfterFade = music
            musicVolume(0f)
        } else {
            currentlyPlaying?.stop(currentlyPlayingId)
            start(music)
        }
    }

    private fun start(music: StreamBpm) {
        currentlyPlayingId = music.stream.play(
            volume = music.basicVolume * masterVolume * uiMusicVolume,
            referenceDistance = 10000f,
            rolloffFactor = 0f,
            loop = true
        )
        music.stream.setPlaybackRate(currentlyPlayingId, playbackRate.toFloat())
        bpm = music.bpm
        currentlyPlaying = music.stream
        time = music.offset
        secondsPerBeat = 60f / music.bpm
        doubleSecondsPerBeat = secondsPerBeat * 2f
        secondsPerMeasure = secondsPerBeat * 4
        currentlyPlayingTrack = music
        uiMusicVolume = 1f
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
        if (isFullyStopped) {
            bpmBasedDt = dt.times(playbackRate)
            return
        }
        if (uiMusicVolumeChangeIn > 0f) {
            uiMusicVolumeChangeIn -= dt.seconds
            if (uiMusicVolumeChangeIn <= 0f) {
                uiMusicVolume = targetUiMusicVolume
                nextTrackAfterFade?.let {
                    currentlyPlaying?.stop(currentlyPlayingId)
                    nextTrackAfterFade = null
                    start(it)
                }
            } else {
                uiMusicVolume =
                    lastUiMusicVolume + ((defaultUiMusicVolumeChangeIn - uiMusicVolumeChangeIn) / defaultUiMusicVolumeChangeIn) * (targetUiMusicVolume - lastUiMusicVolume)
            }
        }

        playbackRateBasedDt = dt.times(playbackRate)
        time += playbackRateBasedDt.seconds
        toBeat = (time % secondsPerBeat) / secondsPerBeat
        toMeasure = (time % secondsPerMeasure) / secondsPerMeasure
        bpmBasedDt =
            playbackRateBasedDt//if (bpm > 0f) playbackRateBasedDt.times(bpm / 150.0) else playbackRateBasedDt
    }

    fun uiSound(sound: AudioClipEx, volume: Float, loop: Boolean = false, onEnd: ((Int) -> Unit)? = null): Int {
        everActiveSoundClips.add(sound)
        val id = sound.play(
            positionX = xPosition,
            positionY = yPosition,
            volume = volume * masterVolume,
            loop = loop,
            onEnded = onEnd
        )
        sound.setPlaybackRate(id, playbackRate.toFloat())
        return id
    }

    fun sound(
        sound: AudioClipEx,
        x: Float,
        y: Float,
        looping: Boolean = false,
        volume: Float = 1f
    ): Int {
        if (x >= xPosition - Game.visibleWorldWidth / 1.5f && x <= xPosition + Game.visibleWorldWidth / 1.5f &&
            y >= yPosition - Game.visibleWorldHeight / 1.5f && y <= yPosition + Game.visibleWorldHeight / 1.5f
        ) {
            everActiveSoundClips.add(sound)
            val id = sound.play(
                positionX = x,
                positionY = y,
                volume = masterVolume * volume,
                loop = looping
            )
            sound.setPlaybackRate(id, playbackRate.toFloat())
            return id
        } else {
            return -1
        }
    }

    fun soundIgnoringPlaybackRate(
        sound: AudioClipEx,
        x: Float,
        y: Float,
        looping: Boolean = false
    ): Int {
        val id = sound.play(positionX = x, positionY = y, volume = masterVolume, loop = looping)
        return id
    }

    fun holdMagicMusic() {
        holdTrack = true
    }

    fun releaseMagicMusic() {
        holdTrack = false
    }


    fun musicVolume(uiValue: Float) {
        targetUiMusicVolume = uiValue
        lastUiMusicVolume = uiMusicVolume
        uiMusicVolumeChangeIn = defaultUiMusicVolumeChangeIn
    }

    companion object {
        const val defaultUiMusicVolumeChangeIn = 0.4f
    }
}