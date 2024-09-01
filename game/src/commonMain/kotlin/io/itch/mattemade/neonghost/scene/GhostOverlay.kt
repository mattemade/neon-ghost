package io.itch.mattemade.neonghost.scene

import com.littlekt.Context
import com.littlekt.graphics.Camera
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.TextureSlice
import com.littlekt.graphics.shader.ShaderProgram
import com.littlekt.graphics.toFloatBits
import com.littlekt.input.InputMapController
import com.littlekt.math.MutableVec2f
import com.littlekt.math.PI2_F
import com.littlekt.math.PI_F
import com.littlekt.util.seconds
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.pixelPerfectPosition
import io.itch.mattemade.neonghost.shader.ParticleFragmentShader
import io.itch.mattemade.neonghost.shader.ParticleVertexShader
import io.itch.mattemade.neonghost.tempo.Choreographer
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import io.itch.mattemade.utils.render.PixelRender
import io.itch.mattemade.utils.render.createPixelFrameBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration

class GhostOverlay(
    private val context: Context,
    private val assets: Assets,
    private val choreographer: Choreographer,
    private val inputMapController: InputMapController<GameInput>,
    private val shaderProgram: ShaderProgram<ParticleVertexShader, ParticleFragmentShader>,

    ) : Releasing by Self() {

    private val sharedFrameBuffer =
        context.createPixelFrameBuffer(Game.virtualWidth, Game.virtualHeight)
    private val worldRender = PixelRender(
        context,
        sharedFrameBuffer,
        Game.visibleWorldWidth,
        Game.visibleWorldHeight,
        ::updateWorld,
        ::renderWorld,
        clear = true,
        blending = true
    ).releasing()
    val texture = sharedFrameBuffer.textures[0]
    private val ghostAnimations = assets.animation.ghostGrayAnimations
    private var currentAnimation = ghostAnimations.fly

    private fun interpolate(value: Float): Float = 3 * value * value - 2 * value * value * value

    private var time = - PI_F  / 2f * 0.9f
    private var startingPeriod = - 5f / 6f
    private var isFacingLeft = false
    private var fadingAppearance = 0f
    private var tempFadingColor = MutableColor(1f, 1f, 1f, 0f)
    var isActive = false
    var isMoving = false

    private val halfScreenWidth = Game.visibleWorldWidth / 2f
    private val halfScreenHeight = Game.visibleWorldHeight / 2f
    val ghostPosition = MutableVec2f(halfScreenWidth, halfScreenWidth)

    private var ghostAppear: GhostAppear? = null

    fun appear() {
        println("appearing")
        isActive = true
        ghostAppear = GhostAppear(
            context,
            assets,
            inputMapController,
            shaderProgram,
            complete = {
                println("appearing complete")
                isMoving = true
                fadingAppearance = 1f
                ghostAppear = null
            }
        )
    }

    private fun updateWorld(dt: Duration, camera: Camera) {
        camera.position.set(halfScreenWidth, halfScreenHeight, 0f)
        ghostAppear?.let {
            if (it.update(dt.seconds)) {
                ghostAppear = null
            }
            return
        }
        if (fadingAppearance > 0f) {
            fadingAppearance -= dt.seconds / 3f
        }
        currentAnimation.update(dt)
        if (isMoving) {
            currentAnimation.currentKeyFrame?.let { frame ->
                time += dt.seconds

                val prevX = ghostPosition.x
                val halfWidthBounds = (Game.visibleWorldWidth - frame.width * Game.IPPU) / 2f
                val halfHeightBounds = (Game.visibleWorldHeight - frame.height * Game.IPPU) / 2f
                ghostPosition.set(
                    halfWidthBounds + halfWidthBounds * cos(time * 0.6f + startingPeriod) + frame.width * Game.IPPU / 2f,
                    halfHeightBounds + halfHeightBounds * sin(time * 1.5f + startingPeriod) + frame.height * Game.IPPU
                )
                val xMovement = ghostPosition.x - prevX
                if (isFacingLeft && xMovement > 0f) {
                    isFacingLeft = false
                } else if (!isFacingLeft && xMovement < 0f) {
                    isFacingLeft = true
                }
            }
        }
    }

    private fun renderWorld(dt: Duration, camera: Camera, batch: Batch) {
        ghostAppear?.let {
            it.render(batch)
            return
        }
        if (isActive) {
            currentAnimation.currentKeyFrame?.let { frame ->
                val width = frame.width * Game.IPPU
                val height = frame.height * Game.IPPU
                batch.draw(
                    frame,
                    ghostPosition.x - width / 2f,
                    ghostPosition.y - height,
                    width = width,
                    height = height,
                    flipX = isFacingLeft,
                )
            }
        }
        if (fadingAppearance > 0f) {
            tempFadingColor.a = interpolate(fadingAppearance)
            batch.draw(
                assets.texture.white,
                0f,
                0f,
                width = Game.visibleWorldWidth.toFloat(),
                height = Game.visibleWorldWidth.toFloat(),
                colorBits = tempFadingColor.toFloatBits()
            )
        }
        neonGhostSlice?.let { frame ->
            val width = frame.width * Game.IPPU
            val height = frame.height * Game.IPPU
            val xOffset = (frame.width * 0.1f / Game.PPU).pixelPerfectPosition
            val yOffset = (3f / Game.PPU).pixelPerfectPosition
            batch.draw(
                frame,
                neonGhostX - width / 2f + if (neonGhostFacingLeft) -xOffset else xOffset,
                neonGhostY - height + yOffset,
                width = width,
                height = height,
                flipX = neonGhostFacingLeft,
            )
        }
    }

    fun updateAndRender(dt: Duration) {
        if (!isActive && neonGhostSlice == null && !renderJustOneMoreTime) {
            return
        }
        if (renderJustOneMoreTime) {
            renderJustOneMoreTime = false
        }
        worldRender.render(dt)
    }

    fun activate() {
        isActive = true
        isMoving = true
    }

    private var renderJustOneMoreTime = false
    private var neonGhostSlice: TextureSlice? = null
    private var neonGhostFacingLeft: Boolean = false
    private var neonGhostX = 0f
    private var neonGhostY = 0f
    fun renderNeonGhost(frame: TextureSlice?, isFacingLeft: Boolean, x: Float, y: Float) {
        neonGhostSlice = frame
        neonGhostFacingLeft = isFacingLeft
        neonGhostX = x
        neonGhostY = y
        if (frame == null) {
            renderJustOneMoreTime = true
        }
    }

    companion object {
        const val radiusX = 48f * Game.IPPU
        const val radiusY = 24f * Game.IPPU
        const val castTime = 2f
        const val castCooldown = 2f
        const val postCastTime = 2f
    }
}
