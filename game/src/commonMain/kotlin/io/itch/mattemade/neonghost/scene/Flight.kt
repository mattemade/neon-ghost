package io.itch.mattemade.neonghost.scene

import com.littlekt.Context
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.graphics.shader.ShaderProgram
import com.littlekt.graphics.toFloatBits
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.character.rei.Player
import io.itch.mattemade.neonghost.pixelPerfectPosition
import io.itch.mattemade.neonghost.shader.ParticleFragmentShader
import io.itch.mattemade.neonghost.shader.ParticleVertexShader
import io.itch.mattemade.neonghost.shader.Particler
import io.itch.mattemade.neonghost.touch.CombinedInput
import io.itch.mattemade.utils.math.fill
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration

class Flight(
    private val player: Player,
    context: Context,
    private val assets: Assets,
    private val input: CombinedInput,
    particleShader: ShaderProgram<ParticleVertexShader, ParticleFragmentShader>,
    val onFirstComplete: () -> Unit,
    val onSecondComplete: () -> Unit,
) {

    private val slice = assets.animation.magicalReiAnimations.idle.run {
        update(Duration.ZERO)
        currentKeyFrame!!
    }
    val width = slice.width
    val height = slice.height
    val textureData = slice.texture.textureData
    val centerX = player.x * 2f
    val centerY = player.y * 2f
    val frameXOffset = (slice.width * 0.1f / Game.PPU).pixelPerfectPosition
    val frameYOffset = (3f / Game.PPU).pixelPerfectPosition
    val xOffset = centerX - width / 2f * Game.IPPU - frameXOffset * 3f - Game.IPPU
    val yOffset = centerY - height * 2f * Game.IPPU + frameYOffset * 1.5f + 2f * Game.IPPU

    private val tempColor = MutableColor()
    private val doubles = 1


    private var tintColor = MutableColor(0f, 0f, 0f, 0f)
    private var isFirstStage = true
    private val firstParticler = Particler(
        context,
        particleShader,
        0f,
        width * height * doubles,
        11000f,
        2f * Game.IPPU,
        interpolation = 3,
        fillData = { index, startColor, endColor, startPosition, endPosition, activeBetween ->
            val x = (index / doubles) % width
            val y = (index / doubles) / width
            val pixelColor = textureData.pixmap.get(slice.x + x, slice.y + y)
            if (pixelColor == 0) {
                startColor.fill(0f)
                endColor.fill(0f)
                startPosition.fill(0f)
                endPosition.fill(0f)
                activeBetween.fill(0f)
            } else {
                tempColor.setRgba8888(pixelColor)
                startColor.fill(1f, 1f, 1f, tempColor.a)
                //endColor.fill(1f, 1f, 1f, tempColor.a)
                endColor.fill(tempColor.r, tempColor.g, tempColor.b, tempColor.a)
                startPosition.fill(
                    centerX + Game.visibleWorldWidth * (Random.nextFloat() - 0.5f) * 8f,
                    centerY + Game.visibleWorldHeight * (Random.nextFloat() - 0.5f) * 8f
                )
                endPosition.fill(
                    xOffset + x * 2 / Game.PPU,
                    yOffset + y * 2 / Game.PPU
                )//xOffset + width * 2 - x / Game.PPU, yOffset + y / Game.PPU)

                activeBetween[0] = /*5000f +*/ Random.nextFloat() * 2000f
                activeBetween[1] = activeBetween[0] + 4000f + Random.nextFloat() * 4000f
            }
        },
        die = {
            onFirstComplete()
        }
    )
    private val secondParticler = Particler(
        context,
        particleShader,
        0f,
        width * height * doubles,
        11000f,
        2f * Game.IPPU,
        interpolation = 3,
        fillData = { index, startColor, endColor, startPosition, endPosition, activeBetween ->
            val x = (index / doubles) % width
            val y = (index / doubles) / width
            val pixelColor = textureData.pixmap.get(slice.x + x, slice.y + y)
            if (pixelColor == 0) {
                startColor.fill(0f)
                endColor.fill(0f)
                startPosition.fill(0f)
                endPosition.fill(0f)
                activeBetween.fill(0f)
            } else {
                tempColor.setRgba8888(pixelColor)
                startColor.fill(1f, 1f, 1f, tempColor.a)
                //endColor.fill(1f, 1f, 1f, tempColor.a)
                endColor.fill(tempColor.r, tempColor.g, tempColor.b, tempColor.a)
                startPosition.fill(
                    centerX + Game.visibleWorldWidth * (Random.nextFloat() - 0.5f) * 8f,
                    centerY + Game.visibleWorldHeight * (Random.nextFloat() - 0.5f) * 8f
                )
                endPosition.fill(
                    xOffset + x * 2 / Game.PPU,
                    yOffset + y * 2 / Game.PPU
                )//xOffset + width * 2 - x / Game.PPU, yOffset + y / Game.PPU)

                activeBetween[0] = /*5000f +*/ Random.nextFloat() * 2000f
                activeBetween[1] = activeBetween[0] + 4000f + Random.nextFloat() * 4000f
            }
        },
        die = {
            onSecondComplete()
        }
    )
    fun nextStage() {
        isFirstStage = false
    }

    init {
        player.isFacingLeft = false
    }

    private var presses = 0
    private var fadeoutDelay = 2f
    private var fadeInDelay = 0f


    fun update(seconds: Float): Boolean {
        if (fadeoutDelay > 0f) {
            fadeoutDelay = max(0f, fadeoutDelay - seconds)
            tintColor.a = 1f - fadeoutDelay / 2f
            return false
        }
        if (fadeInDelay > 0f) {
            fadeInDelay = max(0f, fadeInDelay - seconds)
            tintColor.a = fadeInDelay / 2f
            return false
        }
        if (isFirstStage) {
            firstParticler.update(Duration.ZERO, seconds * 1000f, Duration.ZERO, 0f, 0f, false)
        } else {
            secondParticler.update(Duration.ZERO, seconds * 1000f, Duration.ZERO, 0f, 0f, false)
        }
        /*if (input.pressed(GameInput.ANY_ACTION)) {
            presses++
            if (presses > 2) {
                complete()
                return true
            }
        }*/
        return false
    }

    private var shapeRenderer: ShapeRenderer? = null
    fun render(batch: Batch) {
        if (shapeRenderer == null) {
            shapeRenderer = ShapeRenderer(batch, assets.texture.white)
        }
        shapeRenderer?.filledRectangle(
            x = player.x - Game.halfVisibleWorldWidth,
            y = player.y - Game.halfVisibleWorldHeight,
            width = Game.visibleWorldWidth.toFloat(),
            height = Game.visibleWorldHeight.toFloat(),
            color = tintColor.toFloatBits()
        )
        //player.update(Duration.ZERO, 0f, Duration.ZERO)
        player.render(batch)
        firstParticler.render(batch)
    }
}