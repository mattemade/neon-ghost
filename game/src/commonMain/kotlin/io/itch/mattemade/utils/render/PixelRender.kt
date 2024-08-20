package io.itch.mattemade.utils.render

import com.littlekt.Context
import com.littlekt.graphics.Camera
import com.littlekt.graphics.Color
import com.littlekt.graphics.FrameBuffer
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.SpriteBatch
import com.littlekt.graphics.gl.ClearBufferMask
import com.littlekt.util.Scaler
import com.littlekt.util.viewport.ScalingViewport
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import kotlin.time.Duration

class PixelRender(
    private val context: Context,
    private val target: FrameBuffer,
    worldWidth: Int,
    worldHeight: Int,
    private val preRenderCall: (dt: Duration, camera: Camera) -> Unit,
    private val renderCall: (dt: Duration, camera: Camera, batch: Batch) -> Unit,
    private val clear: Boolean = false,
) : Releasing by Self() {

    private val batch = SpriteBatch(context).releasing()
    private val targetViewport =
        ScalingViewport(scaler = Scaler.Fit(), worldWidth, worldHeight).apply {
            update(target.width, target.height, context, false)
        }
    private val targetCamera = targetViewport.camera

    fun render(dt: Duration) {
        preRenderCall(dt, targetCamera)
        target.begin()
        if (clear) {
            context.gl.clear(ClearBufferMask.COLOR_BUFFER_BIT)
            context.gl.clearColor(Color.CLEAR)
        }
        targetViewport.apply(context)
        batch.begin(targetCamera.viewProjection)
        renderCall(dt, targetCamera, batch)
        batch.end()
        target.end()
    }
}