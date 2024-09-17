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
        var knowledge = mutableSetOf<String>().apply {
            add("training")
            //add("no_tools")
            add("tools")
            add("ghost")
        }
        var eventState = mutableMapOf<String, Int>().apply {
            put("enter_home", 1)
            put("officer_catch", 3)
        }
        var door: String = "security_lift_exit"
        var room: String = "roof"
        var activeMusic: String = "stop"
        var argument = false
        var savedState: Game.SavedState? = null

        if (argument) {
            knowledge.add("training")
            //knowledge.add("magic")
            //knowledge.add("ghost")
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
        Game(it, onLowPerformance = {0f}, savedStateOverride = savedState)
    }
}