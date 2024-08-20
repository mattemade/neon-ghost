package io.itch.mattemade.utils.render

import io.itch.mattemade.neonghost.Game.Companion.virtualHeight
import io.itch.mattemade.neonghost.Game.Companion.virtualWidth
import com.littlekt.Context
import com.littlekt.graphics.Camera
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.SpriteBatch
import com.littlekt.util.Scaler
import com.littlekt.util.viewport.ScalingViewport
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import kotlin.time.Duration

class DirectRender(
    private val context: Context,
    width: Int,
    height: Int,
    private val updateCall: (dt: Duration, camera: Camera) -> Unit,
    private val renderCall: (dt: Duration, batch: Batch) -> Unit,
): Releasing by Self() {

    val postViewport = ScalingViewport(scaler = Scaler.Fit(), width, height)
    val postCamera = postViewport.camera
    val postBatch = SpriteBatch(context).releasing()

    fun resize(width: Int, height: Int) {
        postViewport.virtualWidth = width.toFloat()
        postViewport.virtualHeight = height.toFloat()
        postViewport.update(width, height, context)
    }

    fun render(dt: Duration) {
        updateCall(dt, postCamera)
        postViewport.apply(context)
        postCamera.update()
        postBatch.begin(postCamera.viewProjection)
        renderCall(dt, postBatch)
        postBatch.end()
    }

}