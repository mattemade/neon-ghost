package io.itch.mattemade.utils.drawing

import com.littlekt.graph.node.resource.HAlign
import com.littlekt.graph.node.resource.VAlign
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.TextureSlice

class MonoSpaceTextDrawer(
    private val font: TextureSlice,
    private val alphabet: String,
    private val fontLetterWidth: Int,
    private val fontLetterHeight: Int,
    private val fontHorizontalSpacing: Int = 0,
    private val fontVerticalSpacing: Int = 0,
    private val fontHorizontalPadding: Int = 0,
    private val fontVerticalPadding: Int = 0,
    val drawingLetterWidth: Float = fontLetterWidth.toFloat(),
    val drawingLetterHeight: Float = fontLetterHeight.toFloat(),
    val drawingHorizontalSpacing: Float = 1f,
    val drawingVerticalSpacing: Float = 0f,
) {

    private val letters = mutableMapOf<Char, TextureSlice>().apply {
        var column = 0
        var row = 0
        val letterWidthWithSpacing = fontLetterWidth + fontHorizontalSpacing
        val letterHeightWithSpacing = fontLetterHeight + fontVerticalSpacing
        var yPosition = fontVerticalPadding
        alphabet.forEach { key ->
            val xPosition = ((column++) * letterWidthWithSpacing + fontHorizontalPadding).let {
                if (it > font.width) {
                    column = 0
                    yPosition = (++row) * letterHeightWithSpacing + fontVerticalPadding
                    0
                } else {
                    it
                }
            }

            put(
                key,
                TextureSlice(
                    font,
                    xPosition,
                    yPosition,
                    fontLetterWidth,
                    fontLetterHeight
                )
            )
        }

    }

    private val Int.drawingWidth: Float
        get() = this * fontLetterWidth + (this - 1) * drawingHorizontalSpacing

    private val String.drawingWidth: Float
        get() = length.drawingWidth

    /** @return number of drawn characters: caller could use it to check if it is changed  */
    fun drawText(
        batch: Batch,
        text: List<String>,
        x: Float,
        y: Float,
        hAlign: HAlign = HAlign.CENTER,
        vAlign: VAlign = VAlign.TOP,
        characterLimit: Int = Int.MAX_VALUE,
    ): Int {
        if (text.size == 0) {
            return 0
        }
        val textBoxWidth = text.maxOf { it.length }.drawingWidth
        val textBoxHeight = text.size.let { it * drawingLetterHeight + (it-1) * drawingVerticalSpacing }
        val startPositionX: Float = when (hAlign) {
            HAlign.RIGHT -> x
            HAlign.LEFT -> x - textBoxWidth
            HAlign.CENTER -> x - textBoxWidth/2f
            else -> throw IllegalArgumentException("Provide a correct hAlign instead of $hAlign")
        }
        val startPositionY: Float = when (vAlign) {
            VAlign.TOP -> y
            VAlign.BOTTOM -> y - textBoxHeight
            VAlign.CENTER -> y - textBoxHeight/2f
            else -> throw IllegalArgumentException("Provide a correct vAlign instead of $vAlign")
        }
        var drawnCharacters = 0
        val rows = text.size
        text.forEachIndexed { row, line ->
            val lineStartX = startPositionX + (textBoxWidth - line.length.drawingWidth) / 2f
            line.forEachIndexed { column, char ->
                if (/*char != ' ' && */drawnCharacters < characterLimit) {
                    drawnCharacters++
                    letters[char]?.let { letter ->
                        batch.draw(
                            letter,
                            x = (lineStartX + column * (drawingLetterWidth + drawingHorizontalSpacing)),
                            y = (startPositionY + textBoxHeight - (rows - row) * (drawingLetterHeight + drawingVerticalSpacing)),
                            width = drawingLetterWidth,
                            height = drawingLetterHeight
                        )
                    }
                }
            }
        }
        return drawnCharacters
    }
}
