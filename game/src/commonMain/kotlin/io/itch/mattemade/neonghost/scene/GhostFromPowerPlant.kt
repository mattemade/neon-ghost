package io.itch.mattemade.neonghost.scene

import com.littlekt.Context
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.tilemap.tiled.TiledObjectLayer
import com.littlekt.graphics.gl.PixmapTextureData
import com.littlekt.graphics.shader.ShaderProgram
import com.littlekt.input.InputMapController
import com.littlekt.math.MutableVec2f
import com.littlekt.math.Vec2f
import com.littlekt.math.random
import com.soywiz.kds.fastCastTo
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.LevelSpec
import io.itch.mattemade.neonghost.character.rei.Player
import io.itch.mattemade.neonghost.shader.ParticleFragmentShader
import io.itch.mattemade.neonghost.shader.ParticleVertexShader
import io.itch.mattemade.neonghost.shader.Particler
import io.itch.mattemade.utils.math.fill
import kotlin.random.Random
import kotlin.time.Duration

class GhostFromPowerPlant(
    player: Player,
    context: Context,
    assets: Assets,
    levelSpec: LevelSpec,
    private val input: InputMapController<GameInput>,
    particleShader: ShaderProgram<ParticleVertexShader, ParticleFragmentShader>,
    val complete: () -> Unit
) {

    private val slice = assets.animation.ghostGrayAnimations.idle.run {
        update(Duration.ZERO)
        currentKeyFrame!!
    }

    val centerPoint = levelSpec.level.layer("trigger").fastCastTo<TiledObjectLayer>().objects.first { it.name == "power_plant" }.let {
        val mapHeight = levelSpec.level.height * levelSpec.level.tileHeight
        MutableVec2f(
            it.bounds.x + it.bounds.width / 2f,
            mapHeight - (it.bounds.y - it.bounds.height / 2f)
        ).scale(Game.IPPU)
    }
    val quadrants = listOf(
        Vec2f(-1f, 0f),
        Vec2f(1f, 0f),
        Vec2f(0f, -1f),
        Vec2f(0f, 1f),
        Vec2f(-1f, 1f),
        Vec2f(1f, 1f),
        Vec2f(1f, -1f),
        Vec2f(-1f, -1f),
    )

    val width = slice.width
    val height = slice.height
    val textureData = slice.texture.textureData
    val centerX = Game.virtualWidth.toFloat() - width /*/ 2f*///centerPoint.x * 2f
    val centerY = Game.virtualHeight.toFloat() - height /*/ 2f*///centerPoint.y * 2f
    private val tempColor = MutableColor()
    private val doubles = 3
    private val particler = Particler(
        context,
        particleShader,
        0f,
        width * height * doubles,
        11000f,
        2f/* * Game.IPPU*/,
        interpolation = 1,
        fillData = {
            index, startColor, endColor, startPosition, endPosition, activeBetween ->
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
                startColor.fill(tempColor.r, tempColor.g, tempColor.b, 0f)
                //endColor.fill(1f, 1f, 1f, tempColor.a)
                endColor.fill(tempColor.r, tempColor.g, tempColor.b, 1f)
                startPosition.fill(
                    centerX + x * 2,
                    centerY + y * 2
                )
                val quadraint = quadrants.random()
                endPosition.fill(
                    startPosition[0] + Game.virtualWidth * (Random.nextFloat() - 0.5f) * 2f + Game.virtualWidth * quadraint.x * 3f,
                    startPosition[1] + Game.virtualHeight * (Random.nextFloat() - 0.5f) * 2f + Game.virtualHeight * quadraint.y * 3f
                    /*centerX + Game.virtualWidth * (if (Random.nextBoolean()) 1f else -1f),
                    centerY + Game.virtualHeight * (if (Random.nextBoolean()) 1f else -1f)*/
                )
                /*
                * startPosition.fill(
                    centerX + Game.virtualWidth * (Random.nextFloat() - 0.5f) * 8f,
                    centerY + Game.virtualHeight * (Random.nextFloat() - 0.5f) * 8f
                )
                endPosition.fill(
                    centerX + x * 2,
                    centerY + y * 2
                )*/

                activeBetween[0] = /*5000f +*/ Random.nextFloat() * 3000f
                activeBetween[1] = activeBetween[0] + 3000f + Random.nextFloat() * 3000f
            }
        },
        die = {
            complete()
        }
    )

    private var presses = 0


    init {
        centerX
    }

    fun update(seconds: Float): Boolean {
        particler.update(Duration.ZERO, seconds * 1000f, Duration.ZERO, 0f, 0f, false)
        /*if (input.pressed(GameInput.ANY_ACTION)) {
            presses++
            if (presses > 2) {
                complete()
                return true
            }
        }*/
        return false
    }

    fun render(batch: Batch) {
        particler.render(batch)
    }
}