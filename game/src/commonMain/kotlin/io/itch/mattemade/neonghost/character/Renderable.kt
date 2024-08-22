package io.itch.mattemade.neonghost.character

import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.shape.ShapeRenderer

interface Renderable {
    fun render(batch: Batch)
    fun renderShadow(shapeRenderer: ShapeRenderer)
}