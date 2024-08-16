package io.itch.mattemade.utils.animation

import com.littlekt.file.vfs.VfsFile
import com.littlekt.graphics.g2d.Animation
import com.littlekt.graphics.g2d.AnimationPlayer
import com.littlekt.graphics.g2d.TextureSlice
import io.itch.mattemade.utils.atlas.RuntimeTextureAtlasPacker
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class AnimationSpec(
    val path: String,
    private val formatPrefix: String,
    private val formatSuffix: String?,
    private val formatNumberDigits: Int,
    val framesCount: Int,
    val frameSpecs: List<AnimationFrameSpec>,
    val signals: Map<Int, String>
) {
    fun getFramePath(frame: Int): String {
        if (formatNumberDigits == 0) {
            return "$path/$formatPrefix"
        }
        val frameString = frame.toString()
        val frameLength = frameString.length
        if (frameLength > formatNumberDigits) {
            return "$path/$formatPrefix$frameString$formatSuffix"
        } else {
            var trailingZeros = ""
            for (i in 1..(formatNumberDigits - frameLength)) {
                trailingZeros += "0"
            }
            return "$path/$formatPrefix$trailingZeros$frameString$formatSuffix"
        }
    }
}

data class AnimationFrameSpec(
    val frameIndicies: List<Int>,
    val frameDuration: List<Duration>,
    val repeatLogic: String,
)

data class AnimationPlayerSpec(
    val player: AnimationPlayer<TextureSlice>,
    val animation: Animation<TextureSlice>,
    val limitRepeats: Int = 0,
    val duration: Duration,
)

private data class TextureUsageCounter(
    var slice: TextureSlice,
    var counter: Int,
)

suspend fun VfsFile.readAnimationPlayer(
    runtimeTextureAtlasPacker: RuntimeTextureAtlasPacker,
    signalCallback: ((String) -> Unit)? = null,
): SignallingAnimationPlayer =
    readAnimationSpec().let { spec ->
        val textureCache = mutableMapOf<String, TextureUsageCounter>()
        (1..spec.framesCount).forEach {
            val textureUsage = textureCache.getOrPut(spec.getFramePath(it)) {
                TextureUsageCounter(
                    runtimeTextureAtlasPacker.pack(spec.getFramePath(it), vfs).await(),
                    0
                )
            }
            textureUsage.counter++
        }
        val sliceCache = mutableMapOf<Int, TextureSlice>()
        fun getSlice(index: Int): TextureSlice =
            sliceCache.getOrPut(index) {
                var remainder = index
                for (i in 1..spec.framesCount) {
                    val textureUsage = textureCache[spec.getFramePath(i)]!!
                    if (remainder >= textureUsage.counter) {
                        remainder -= textureUsage.counter
                    } else {
                        val sliceWidth = textureUsage.slice.width / textureUsage.counter
                        return textureUsage.slice.slice(x = remainder * sliceWidth, y = 0, width = sliceWidth, height = textureUsage.slice.height)
                    }
                }
                error("Out of bounds")
            }
        val framesPerPlayer = mutableListOf<Int>()
        val players = spec.frameSpecs.map { frameSpec ->
            framesPerPlayer += frameSpec.frameIndicies.size
            val limitRepeats =
                if (frameSpec.repeatLogic.isBlank()) 0 else frameSpec.repeatLogic.toInt()
            val totalFrameDurations =
                frameSpec.frameDuration.reduce { acc, duration -> acc + duration }
            AnimationPlayerSpec(
                player = AnimationPlayer(),
                animation = Animation(
                    frames = (0..<frameSpec.frameIndicies.size).map { getSlice(frameSpec.frameIndicies[it]) },
                    frameIndices = frameSpec.frameIndicies.indices.toList(),
                    frameTimes = frameSpec.frameDuration,
                ),
                limitRepeats = limitRepeats,
                duration = totalFrameDurations * limitRepeats,
            )

        }

        SignallingAnimationPlayer(players, framesPerPlayer, spec.signals, signalCallback)
    }

suspend fun VfsFile.readAnimationSpec(): AnimationSpec {
    val specText = get("spec.txt").readLines().map { it.trim() }

    var frameIndices = mutableListOf<Int>()
    var frameTimes = mutableListOf<Duration>()
    val signals = mutableMapOf<Int, String>()
    var repeatLogic = "1"
    var framesCount = 0
    var format = ""
    val frameSpecs = mutableListOf<AnimationFrameSpec>()
    var framesPerAllPreviousAnimations = 0
    specText.forEach { line ->
        if (!line.startsWith("#")) {
            val formatSplit = line.split(" frames like ")
            val sequenceSplit = line.split(" for ")
            if (formatSplit.size == 2) {
                framesCount = formatSplit[0].toInt()
                format = formatSplit[1]
            } else if (sequenceSplit.size == 2) {
                val rangeSplit = sequenceSplit[0].split("..")
                val frameTime = sequenceSplit[1].toInt().milliseconds
                if (rangeSplit.size == 2) {
                    val start = rangeSplit[0].toInt()
                    val end = rangeSplit[1].toInt()
                    val range = if (start < end) start..end else start downTo end
                    for (frame in range) {
                        frameIndices += frame - 1
                        frameTimes += frameTime
                    }
                } else {
                    frameIndices += sequenceSplit[0].toInt() - 1
                    frameTimes += frameTime
                }
            } else if (line.startsWith("repeat")) {
                repeatLogic = if (line.length > 7) line.substring(7) else ""
                frameSpecs += AnimationFrameSpec(
                    frameIndices,
                    frameTimes,
                    repeatLogic,
                )
                framesPerAllPreviousAnimations += frameIndices.size
                frameIndices = mutableListOf<Int>()
                frameTimes = mutableListOf<Duration>()
                repeatLogic = ""
            } else if (line.startsWith("signal")) {
                val signal = if (line.length > 7) line.substring(7) else ""
                signals[framesPerAllPreviousAnimations + frameIndices.size] = signal
            }
        }
    }
    val formatSplit = format.split(Regex("%+"))

    return AnimationSpec(
        path,
        formatPrefix = formatSplit[0],
        formatSuffix = formatSplit.getOrNull(1),
        format.count { it == '%' },
        framesCount,
        frameSpecs,
        signals
    )
}
/*
7 frames like walk.%%%%.png
1..7 for 16
repeat from start
*/
