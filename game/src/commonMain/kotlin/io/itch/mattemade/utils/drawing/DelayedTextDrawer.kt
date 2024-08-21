package io.itch.mattemade.utils.drawing

import com.littlekt.graph.node.resource.HAlign
import com.littlekt.graph.node.resource.VAlign
import com.littlekt.graphics.g2d.Batch

class DelayedTextDrawer(private val textDrawer: MonoSpaceTextDrawer, private val timePerCharacter: () -> Float) {

    private var internalTimer = 0f
    private var text: List<String> = emptyList()
    private var x: Float = 0f
    private var y: Float = 0f
    private var currentCharacterLimit = 0
    private var previouslyDrawnCharacters = -1
    var isFinished: Boolean = false
      private set

    fun startDrawing(text: List<String>, x: Float, y: Float) {
        internalTimer = 0f
        this.text = text
        this.x = x
        this.y = y
        currentCharacterLimit = 0
        previouslyDrawnCharacters = -1
        isFinished = false
    }

    fun advance() {
        currentCharacterLimit = Int.MAX_VALUE
        isFinished = true
    }

    /** @return true if finished */
    fun updateAndDraw(delta: Float, batch: Batch): Boolean {
        internalTimer += delta
        var isLimitChanged = false
        if (!isFinished) {
            val previousLimit = currentCharacterLimit
            currentCharacterLimit = (internalTimer / timePerCharacter()).toInt()
            isLimitChanged = previousLimit != currentCharacterLimit
        }

        val drawnCharacters = textDrawer.drawText(
            batch,
            text,
            x,
            y,
            hAlign = HAlign.LEFT,
            vAlign = VAlign.TOP,
            characterLimit = currentCharacterLimit
        )
        val isDrawnCharactersChanged = previouslyDrawnCharacters != drawnCharacters
        previouslyDrawnCharacters = drawnCharacters

        if (isLimitChanged && !isDrawnCharactersChanged) {
            isFinished = true
        }
        return isFinished
    }

}
