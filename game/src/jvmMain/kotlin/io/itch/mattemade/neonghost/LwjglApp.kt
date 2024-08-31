package io.itch.mattemade.neonghost

import com.littlekt.createLittleKtApp
import io.itch.mattemade.neonghost.character.rei.Player

fun main() {
    createLittleKtApp {
        width = 960
        height = 540
        title = "Neon Ghost"
    }.start {
        var playerHealth = Player.maxPlayerHealth
        var knowledge = mutableSetOf<String>()
        var eventState = mutableMapOf<String, Int>()
        var door: String = "jetpack"
        var room: String = "roof"
        var argument = true
        /*var door: String = "enter_home"
var room: String = "rei_home"*/
        /*var isMagical = false
        var isGhost = false
        var argument = true*/
        var savedState: Game.SavedState? = null

        if (argument) {
            knowledge.add("training")
            knowledge.add("magic")
            knowledge.add("ghost")
            savedState = Game.SavedState(
                door = door,
                room = room,
                eventState = eventState,
                playerKnowledge = knowledge,
                interactionOverride = mutableMapOf(),
                ghostActive = knowledge.contains("ghost"),
                playerHealth = playerHealth,
                isMagic = knowledge.contains("magic"),
                activeMusic = "stop"
            )
        }
        Game(it, onLowPerformance = {}, savedStateOverride = savedState)
    }
}