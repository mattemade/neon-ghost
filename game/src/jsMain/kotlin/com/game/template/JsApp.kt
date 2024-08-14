package com.game.template

import com.littlekt.Context
import com.littlekt.RemoveContextCallback
import com.littlekt.createLittleKtApp
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement

private const val CANVAS_ID = "canvas"

fun main() {
    createLittleKtApp {
        width = 960
        height = 540
        title = "LittleKt Game Template"
        canvasId = CANVAS_ID
    }.start {
        scheduleCanvasResize(it)
        val game = Game(it)
        window.addEventListener("blur", {
            game.focused = false
        })
        game
    }
}

private fun scheduleCanvasResize(context: Context) {
    var removeContextCallback: RemoveContextCallback? = null
    removeContextCallback = context.onRender {
        // scale the container to allow canvas take all the available device pixel of hi-DPI display
        val canvasContainer = document.getElementById("canvas-container") as HTMLElement
        val zoom = (1 / window.devicePixelRatio).toFloat()
        /*canvasContainer.style.apply {
            this.asDynamic().zoom = "$zoom"
        }*/

        // resize the canvas to fit max available space
        val canvas = document.getElementById(CANVAS_ID) as HTMLCanvasElement
        canvas.style.apply {
            display = "block"
            position = "absolute"
            top = "0"
            bottom = "0"
            left = "0"
            right = "0"
            width = "100%"
            height = "100%"
            this.asDynamic().zoom = "$zoom" // TODO: makes better pixels but impacts performance in firefox
        }
        //canvas.getContext("webgl2").asDynamic().translate(0.5f, 0.5f)
        removeContextCallback?.invoke()
        removeContextCallback = null
    }
}
