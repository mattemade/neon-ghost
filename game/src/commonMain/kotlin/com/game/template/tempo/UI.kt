package com.game.template.tempo

import com.game.template.Game
import com.littlekt.Context
import com.littlekt.graphics.Color
import com.littlekt.graphics.g2d.SpriteBatch
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.graphics.toFloatBits
import com.littlekt.math.MutableVec2f
import com.littlekt.math.PI_F
import com.littlekt.math.Vec2f
import com.littlekt.math.geom.radians
import com.littlekt.util.Scaler
import com.littlekt.util.viewport.ScalingViewport
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self

class UI(private val context: Context) : Releasing by Self() {

    private val batch = SpriteBatch(context).releasing()
    private val shapeRenderer = ShapeRenderer(batch)
    private val viewport =
        ScalingViewport(scaler = Scaler.Fit(), Game.virtualWidth, Game.virtualHeight)
    private val camera = viewport.camera.apply {
        position.set(Game.virtualWidth / 2f, Game.virtualHeight / 2f, 0f)
    }
    private val center = Vec2f(16f, 16f)
    private val tempVec2f = MutableVec2f(0f)
    private val tempVec2fb = MutableVec2f(0f)

    fun render(toMeasure: Float, movingToBeatUnlocked: Boolean, movingToBeat: Boolean) {
        viewport.apply(context)
        batch.begin(camera.viewProjection)
        shapeRenderer.filledCircle(center, 15f, color = Color.WHITE.toFloatBits())
        shapeRenderer.filledCircle(
            center,
            13f,
            color = (if (movingToBeat) Color.DARK_GREEN else if (movingToBeatUnlocked) Color.DARK_YELLOW else Color.DARK_RED).toFloatBits()
        )
        for (i in 0 until 4) {
            tempVec2f.set(15f, 0f).rotate((PI_F * i / 2f).radians).add(center)
            tempVec2fb.set(10f, 0f).rotate((PI_F * i / 2f).radians).add(center)
            shapeRenderer.line(tempVec2fb, tempVec2f, color = Color.WHITE, thickness = 1)
        }
        tempVec2f.set(14f, 0f).rotate((toMeasure * PI_F * 2 - PI_F / 2f).radians).add(center)
        shapeRenderer.line(center, tempVec2f, color = Color.WHITE, thickness = 1)
        batch.end()
    }
}