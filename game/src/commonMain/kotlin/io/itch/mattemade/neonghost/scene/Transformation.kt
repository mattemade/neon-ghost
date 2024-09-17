package io.itch.mattemade.neonghost.scene

import com.littlekt.Context
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.graphics.shader.ShaderProgram
import com.littlekt.graphics.toFloatBits
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.ExtraAssets
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.character.rei.Player
import io.itch.mattemade.neonghost.pixelPerfectPosition
import io.itch.mattemade.neonghost.shader.ParticleFragmentShader
import io.itch.mattemade.neonghost.shader.ParticleVertexShader
import io.itch.mattemade.neonghost.shader.Particler
import io.itch.mattemade.neonghost.tempo.Choreographer
import io.itch.mattemade.neonghost.touch.CombinedInput
import io.itch.mattemade.neonghost.world.CameraMan
import io.itch.mattemade.utils.math.fill
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration

class Transformation(
    private val player: Player,
    private val cameraMan: CameraMan,
    private val choreographer: Choreographer,
    context: Context,
    private val assets: Assets,
    private val extraAssets: ExtraAssets,
    private val input: CombinedInput,
    particleShader: ShaderProgram<ParticleVertexShader, ParticleFragmentShader>,
    val onFirstComplete: () -> Unit,
    val complete: () -> Unit
) {

    private val cloudTexture = assets.texture.clouds
    private val fullCloudLength = Game.visibleWorldHeight + cloudTexture.height * Game.IPPU
    private val cloudWidth = cloudTexture.width * Game.IPPU
    private val cloudHeight = cloudTexture.height * Game.IPPU
    private val cloudX = cameraMan.position.x - cloudWidth /2f
    private var startPassingThroughCloudsIn = 0f
    private var passingThroughClouds = 0f
    private val maxPassingThroughClouds = 3f
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
    private val particler = Particler(
        context,
        particleShader,
        0f,
        width * height * doubles,
        15000f,
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

                activeBetween[0] = 4000f + Random.nextFloat() * 2000f
                activeBetween[1] = activeBetween[0] + 4000f + Random.nextFloat() * 4000f
            }
        },
        die = {
            fadeInDelay = 2f
            //complete()
        }
    )

    val fireWidth = 20
    val fireHeight = 20
    val fireXOffset = -20f * Game.IPPU
    val fireYOffset = -40f * Game.IPPU
    val fireCenterX = cameraMan.position.x * 2f
    val fireCenterY = cameraMan.position.y * 2f
    val fireDoubles = 8

    private var tintColor = MutableColor(0f, 0f, 0f, 0f)
    private var isFirstStage = true
    private var isTransforming = false
    private var firstDead = false
    private var jetpackSoundId = -1
    private var stopJetpackIn = 0f
    private val firstParticler = Particler(
        context,
        particleShader,
        0f,
        fireWidth * fireHeight * fireDoubles,
        6000f,
        2f * Game.IPPU,
        interpolation = 2,
        fillData = { index, startColor, endColor, startPosition, endPosition, activeBetween ->
            val x = (index / fireDoubles) % fireWidth
            val y = (index / fireDoubles) / fireWidth
            startColor.fill(0.5f + Random.nextFloat() * 0.5f, Random.nextFloat() * 0.5f, 0f, 0.5f + Random.nextFloat() * 0.5f)
            endColor.fill(0.5f + Random.nextFloat() * 0.5f, Random.nextFloat() * 0.5f, 0f, 0.5f + Random.nextFloat() * 0.5f)
            startPosition.fill(
                fireCenterX + fireXOffset + x * Game.IPPU,
                fireCenterY + fireYOffset + y * Game.IPPU
            )
            endPosition.fill(
                startPosition[0] + (Random.nextFloat() * 100f - 50f) * Game.IPPU,
                startPosition[1] + 320f * Game.IPPU
            )//xOffset + width * 2 - x / Game.PPU, yOffset + y / Game.PPU)

            activeBetween[0] = Random.nextFloat() * 4000f
            activeBetween[1] = activeBetween[0] + 1000f
        },
        die = {
            if (!firstDead) {
                firstDead = true
                onFirstComplete()
            }
        }
    )
    private var secondDead = false
    private var magicJetpackSoundId = -1
    private var stopMagicJetpackIn = 0f
    private val secondParticler = Particler(
        context,
        particleShader,
        0f,
        fireWidth * fireHeight * fireDoubles,
        4000f,
        2f * Game.IPPU,
        interpolation = 2,
        fillData = { index, startColor, endColor, startPosition, endPosition, activeBetween ->
            val x = (index / fireDoubles) % fireWidth
            val y = (index / fireDoubles) / fireWidth
            val color = 0.5f + Random.nextFloat() * 0.5f
            startColor.fill(color, color, color, 0.5f + Random.nextFloat() * 0.5f)
            endColor.fill(color, color, color, 0.5f + Random.nextFloat() * 0.5f)
            startPosition.fill(
                fireCenterX + fireXOffset + x * Game.IPPU,
                fireCenterY + fireYOffset + y * Game.IPPU
            )
            endPosition.fill(
                startPosition[0] + (Random.nextFloat() * 100f - 50f) * Game.IPPU,
                startPosition[1] + 520f * Game.IPPU
            )//xOffset + width * 2 - x / Game.PPU, yOffset + y / Game.PPU)

            activeBetween[0] = Random.nextFloat() * 3000f
            activeBetween[1] = activeBetween[0] + 1000f
        },
        die = {
            if (!secondDead) {
                isTransforming = true//onSecondComplete()
                secondDead = true
            }
        }
    )
    fun nextStage() {
        if (isFirstStage) {
            isFirstStage = false
            startPassingThroughCloudsIn = 2f
            if (magicJetpackSoundId == -1) {
                magicJetpackSoundId = choreographer.uiSound(extraAssets.sound.magicJetpack, volume = 0.5f, loop = true)
                stopMagicJetpackIn = 4f
            }
        } else if (!isTransforming) {
            isTransforming = true
        }
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
            if (fadeInDelay == 0f) {
                complete()
            }
            return false
        }
        if (stopJetpackIn > 0f) {
            stopJetpackIn = max(0f, stopJetpackIn - seconds)
            if (stopJetpackIn == 0f && jetpackSoundId != -1) {
                extraAssets.sound.jetpack.stop(jetpackSoundId)
            }
        }
        if (stopMagicJetpackIn > 0f) {
            stopMagicJetpackIn = max(0f, stopMagicJetpackIn - seconds)
            if (stopMagicJetpackIn == 0f && magicJetpackSoundId != -1) {
                extraAssets.sound.magicJetpack.stop(magicJetpackSoundId)
                magicJetpackSoundId = -1
            }
        }
        if (isFirstStage) {
            if (!firstDead && jetpackSoundId == -1) {
                jetpackSoundId = choreographer.uiSound(extraAssets.sound.jetpack, volume = 0.5f, loop = true)
                stopJetpackIn = 4f
            }
            firstParticler.update(Duration.ZERO, seconds * 1000f, Duration.ZERO, 0f, 0f, false)
        } else if (!isTransforming) {
            secondParticler.update(Duration.ZERO, seconds * 1000f, Duration.ZERO, 0f, 0f, false)
        } else if (isTransforming) {
            particler.update(Duration.ZERO, seconds * 1000f, Duration.ZERO, 0f, 0f, false)
        }
        if (passingThroughClouds > 0f) {
            passingThroughClouds = max (0f, passingThroughClouds - seconds)
        }
        if (startPassingThroughCloudsIn > 0f) {
            startPassingThroughCloudsIn = max (0f, startPassingThroughCloudsIn - seconds)
            if (startPassingThroughCloudsIn == 0f) {
                passingThroughClouds = maxPassingThroughClouds
            }
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
            x = cameraMan.position.x - Game.halfVisibleWorldWidth,
            y = cameraMan.position.y - Game.halfVisibleWorldHeight,
            width = Game.visibleWorldWidth.toFloat(),
            height = Game.visibleWorldHeight.toFloat(),
            color = tintColor.toFloatBits()
        )
        //player.update(Duration.ZERO, 0f, Duration.ZERO)
        if (isFirstStage) {
            firstParticler.render(batch)
        } else if (!isTransforming) {
            secondParticler.render(batch)
        }
        player.render(batch)
        if (isTransforming) {
            particler.render(batch)
        }

        if (passingThroughClouds > 0f) {
            val offset1 = fullCloudLength * passingThroughClouds / maxPassingThroughClouds
            val offset2 = fullCloudLength * passingThroughClouds / maxPassingThroughClouds * 1.25f
            val offset3 = fullCloudLength * passingThroughClouds / maxPassingThroughClouds * 1.5f
            batch.draw(cloudTexture,
                x = cloudX,
                y = cameraMan.position.y + Game.halfVisibleWorldHeight - offset1,
                width = cloudWidth,
                height = cloudHeight)
            batch.draw(cloudTexture,
                x = cloudX,
                y = cameraMan.position.y  + Game.halfVisibleWorldHeight - offset2,
                width = cloudWidth,
                height = cloudHeight)

            batch.draw(cloudTexture,
                x = cloudX,
                y = cameraMan.position.y  + Game.halfVisibleWorldHeight - offset3,
                width = cloudWidth,
                height = cloudHeight)
        }
    }
}