package io.itch.mattemade.neonghost

import com.littlekt.Context
import com.littlekt.RemoveContextCallback
import com.littlekt.createLittleKtApp
import com.littlekt.log.Logger
import io.itch.mattemade.neonghost.character.rei.Player
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement

private const val CANVAS_ID = "canvas"

external fun decodeURIComponent(encodedURI: String): String

fun main() {
    createLittleKtApp {
        width = 960
        height = 540
        title = "neon ghost"
        canvasId = CANVAS_ID
    }.start {
        Logger.setLevels(Logger.Level.NONE)
        var playerHealth = Player.maxPlayerHealth
        var knowledge = mutableSetOf<String>().apply {
            //add("training")
            //add("no_tools")
            //add("tools")
//            add("ghost")
//            add("card")
        }
        var eventState = mutableMapOf<String, Int>().apply {
            //put("enter_home", 1)
            //put("officer_catch", 1)
        }
        var door: String = "player"
        var room: String = "boxing_club"
        var activeMusic: String = "stop"
        var argument = false
        var cabinet = true
        var savedState: Game.SavedState? = null
        window.location.href.takeIf { it.contains('?') }
            ?.substringAfter('?')
            ?.split("&")
            ?.asSequence()
            ?.forEach {
                argument = true
                val split = it.split("=")
                val key = split[0]
                val value = split.getOrNull(1)?.let { decodeURIComponent(it) }
                when (key) {
                    "hp" -> playerHealth = value?.toInt() ?: Player.maxPlayerHealth
                    "remember" -> knowledge.add(value ?: "")
                    "spawn" -> door = value ?: door
                    "room" -> room = value ?: room
                    "music" -> activeMusic = value ?: activeMusic
                    "cabinet" -> cabinet = true
                    else -> eventState[key] = value?.toIntOrNull() ?: 0
                }
            }
        if (argument) {
            savedState = Game.SavedState(
                door = door,
                room = room,
                eventState = eventState,
                playerKnowledge = knowledge,
                interactionOverride = mutableMapOf(),
                ghostActive = knowledge.contains("ghost"),
                playerHealth = playerHealth,
                isMagic = knowledge.contains("magic"),
                activeMusic = activeMusic
            )
        }
        scheduleCanvasResize(it)
        val game = Game(it, ::onLowPerformance, cabinet, savedState)
        window.addEventListener("blur", {
            game.focused = false
        })
        game
    }
}

val canvas = document.getElementById(CANVAS_ID) as HTMLCanvasElement
private var zoom = 1f
private var zoomFactor = 1f
private fun scheduleCanvasResize(context: Context) {
    var removeContextCallback: RemoveContextCallback? = null
    removeContextCallback = context.onRender {
        zoom = (1 / window.devicePixelRatio).toFloat()
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
            // scale the canvas take all the available device pixel of hi-DPI display
            this.asDynamic().zoom =
                "$zoom" // TODO: makes better pixels but impacts performance in firefox
        }
        //canvas.getContext("webgl2").asDynamic().translate(0.5f, 0.5f)
        removeContextCallback?.invoke()
        removeContextCallback = null
    }
}

private fun onLowPerformance(resetZoom: Boolean) {
    if (resetZoom) {
        zoomFactor = 1f
    } else {
        zoomFactor += 1f
    }
    val setZoom = zoom * zoomFactor
    canvas.style.asDynamic().zoom = "$setZoom"
}