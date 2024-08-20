package io.itch.mattemade.neonghost.character

import com.littlekt.graphics.g2d.Batch

interface Renderable {
    fun render(batch: Batch)
}