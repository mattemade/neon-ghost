package io.itch.mattemade.utils.drawing

import com.littlekt.graph.node.resource.HAlign
import com.littlekt.graph.node.resource.VAlign
import com.littlekt.graphics.g2d.Batch

class DelayedTextDrawer(
    private val textDrawer: MonoSpaceTextDrawer,
    private val timePerCharacter: (c: Char, last: Boolean) -> Float
) {

    private var internalTimer = 0f
    var text: List<String> = emptyList()
    private var x: Float = 0f
    private var y: Float = 0f
    private var currentCharacterLimit = 0
    private var currentLine = 0
    private var currentIndex = 0
    private var nextCharIn = 0f
    var isFinished: Boolean = false
        private set

    fun startDrawing(text: List<String>, x: Float, y: Float) {
        internalTimer = 0f
        this.text = text
        this.x = x
        this.y = y
        currentCharacterLimit = 0
        currentLine = 0
        currentIndex = -1
        nextCharIn = 0f
        isFinished = false
    }

    fun advance() {
        currentCharacterLimit = Int.MAX_VALUE
        isFinished = true
    }

    /** @return true if finished */
    fun updateAndDraw(delta: Float, batch: Batch): Boolean {
        if (!isFinished) {
            nextCharIn -= delta
            while (nextCharIn <= 0f) {
                currentCharacterLimit++
                currentIndex++
                if (currentIndex >= text[currentLine].length) {
                    currentLine++
                    if (currentLine >= text.size) {
                        isFinished = true
                        break
                    }
                    currentIndex = 0
                }
                nextCharIn += timePerCharacter(
                    text[currentLine][currentIndex],
                    currentLine == text.size - 1 && currentIndex == text[currentLine].length - 1
                )
            }
        }

        textDrawer.drawText(
            batch,
            text,
            x,
            y,
            hAlign = HAlign.CENTER,
            vAlign = VAlign.TOP,
            characterLimit = currentCharacterLimit
        )
        return isFinished
    }

}
