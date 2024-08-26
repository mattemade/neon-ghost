package io.itch.mattemade.neonghost.scene

import com.littlekt.Context
import com.littlekt.graphics.Camera
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.TextureSlice
import com.littlekt.math.MutableVec2f
import com.littlekt.util.seconds
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.pixelPerfectPosition
import io.itch.mattemade.neonghost.tempo.Choreographer
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import io.itch.mattemade.utils.render.PixelRender
import io.itch.mattemade.utils.render.createPixelFrameBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration

class GhostOverlay(
    private val context: Context,
    private val assets: Assets,
    private val choreographer: Choreographer,
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

    val ghostPosition = MutableVec2f(0f)
    private fun interpolate(value: Float): Float = 3 * value * value - 2 * value * value * value

    private var time = 0f
    private var isFacingLeft = false
    var isActive = false
    var isMoving = false

    private val halfScreenWidth = Game.visibleWorldWidth / 2f
    private val halfScreenHeight = Game.visibleWorldHeight / 2f

    private fun updateWorld(dt: Duration, camera: Camera) {
        currentAnimation.update(dt)
        if (isMoving) {
            currentAnimation.currentKeyFrame?.let { frame ->
                time += dt.seconds

                val prevX = ghostPosition.x
                val halfWidthBounds = (Game.visibleWorldWidth - frame.width * Game.IPPU) / 2f
                val halfHeightBounds = (Game.visibleWorldHeight - frame.height * Game.IPPU) / 2f
                ghostPosition.set(
                    halfWidthBounds + halfWidthBounds * cos(time * 0.6f) + frame.width * Game.IPPU / 2f,
                    halfHeightBounds + halfHeightBounds * sin(time * 1.5f) + frame.height * Game.IPPU
                )
                val xMovement = ghostPosition.x - prevX
                if (isFacingLeft && xMovement > 0f) {
                    isFacingLeft = false
                } else if (!isFacingLeft && xMovement < 0f) {
                    isFacingLeft = true
                }
            }
        }
        camera.position.set(halfScreenWidth, halfScreenHeight, 0f)
    }

    private fun renderWorld(dt: Duration, camera: Camera, batch: Batch) {
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
