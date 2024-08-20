package com.game.template.tempo

import com.game.template.Game
import com.game.template.character.enemy.Enemy
import com.game.template.character.rei.Player
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

class UI(private val context: Context, private val player: Player) : Releasing by Self() {

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

    private val healthBarWidth = 64f
    private val healthBarHeight = 8f
    private val healthBarPadding = 2f
    private val maxHealthSlots = 5
    private val health = Array<Enemy?>(maxHealthSlots) { null }

    fun render(toMeasure: Float, movingToBeat: Boolean, movingOffbeat: Boolean) {
        viewport.apply(context)
        batch.begin(camera.viewProjection)
        shapeRenderer.filledCircle(center, 15f, color = Color.WHITE.toFloatBits())
        shapeRenderer.filledCircle(
            center,
            13f,
            color = (if (movingToBeat) Color.DARK_GREEN else if (movingOffbeat) Color.DARK_RED else Color.DARK_YELLOW).toFloatBits()
        )
        for (i in 0 until 4) {
            tempVec2f.set(15f, 0f).rotate((PI_F * i / 2f).radians).add(center)
            tempVec2fb.set(10f, 0f).rotate((PI_F * i / 2f).radians).add(center)
            shapeRenderer.line(tempVec2fb, tempVec2f, color = Color.WHITE, thickness = 1)
        }
        tempVec2f.set(14f, 0f).rotate((toMeasure * PI_F * 2 - PI_F / 2f).radians).add(center)
        shapeRenderer.line(center, tempVec2f, color = Color.WHITE, thickness = 1)

        renderProgressbar(
            34f,
            healthBarPadding,
            healthBarWidth,
            healthBarHeight,
            player.health.toFloat() / player.initialHealth,
            Color.GRAY,
            Color.GREEN
        )

        for (i in 0 until maxHealthSlots) {
            val enemy = health[i]
            if (enemy != null) {
                val isLeft = i % 2 == 1
                val row = (i + 1) / 2
                val startX = if (isLeft) 34f else Game.virtualWidth - healthBarWidth - 2f
                val startY = (row + 1) * healthBarPadding + row * healthBarHeight
                renderProgressbar(
                    startX,
                    startY,
                    healthBarWidth,
                    healthBarHeight,
                    enemy.health.toFloat() / enemy.initialHeath,
                    Color.GRAY,
                    Color.RED
                )
            }
        }

        batch.end()
    }

    private fun renderProgressbar(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        progress: Float,
        bgColor: Color,
        color: Color
    ) {
        shapeRenderer.filledRectangle(
            x,
            y,
            width,
            height,
            color = bgColor.toFloatBits()
        )
        shapeRenderer.filledRectangle(
            x,
            y,
            width * progress,
            healthBarHeight,
            color = color.toFloatBits()
        )
    }

    fun showHealth(enemy: Enemy) {
        for (i in 0 until maxHealthSlots) {
            if (health[i] == null) {
                health[i] = enemy
                return
            }
        }
    }

    fun stopShowingHealth(deadEnemies: MutableList<Enemy>) {
        for (i in 0 until maxHealthSlots) {
            if (deadEnemies.contains(health[i])) {
                health[i] = null
            }
        }
    }
}