package io.itch.mattemade.neonghost.scene

import com.littlekt.Context
import com.littlekt.graphics.Camera
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.gl.BlendFactor
import com.littlekt.graphics.gl.State
import com.littlekt.math.MutableVec2f
import com.littlekt.util.seconds
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.Game
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
        clear = true
    ).releasing()
    val texture = sharedFrameBuffer.textures[0]
    private val ghostAnimations = assets.animation.ghostGrayAnimations
    private var currentAnimation = ghostAnimations.fly

    private val ghostPosition = MutableVec2f(0f)
    private fun interpolate(value: Float): Float = 3 * value * value - 2 * value * value * value

    private var time = 0f
    private var isFacingLeft = false


    private val halfScreenWidth = Game.visibleWorldWidth / 2f
    private val halfScreenHeight = Game.visibleWorldHeight / 2f

    private fun updateWorld(dt: Duration, camera: Camera) {
        time += dt.seconds

        val prevX = ghostPosition.x
        ghostPosition.set(
            halfScreenWidth + halfScreenWidth * cos(time * 0.75f),
            halfScreenHeight + halfScreenHeight * sin(time * 1.5f)
        )
        if (isFacingLeft != ghostPosition.x < prevX) {
            isFacingLeft = !isFacingLeft
        }
        currentAnimation.update(dt)
        camera.position.set(halfScreenWidth, halfScreenHeight, 0f)
    }

    private fun renderWorld(dt: Duration, camera: Camera, batch: Batch) {
        currentAnimation.currentKeyFrame?.let { frame ->
            context.gl.enable(State.BLEND)
            batch.setBlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA)
            val width = frame.width * Game.IPPU
            val height = frame.height * Game.IPPU
            batch.draw(
                frame,
                ghostPosition.x - width / 2f,
                ghostPosition.y - height / 2f,
                width = width,
                height = height,
                flipX = isFacingLeft,
            )
            batch.setToPreviousBlendFunction()
            context.gl.disable(State.BLEND)
        }
    }

    fun updateAndRender(dt: Duration) {
        worldRender.render(dt)
    }
}
