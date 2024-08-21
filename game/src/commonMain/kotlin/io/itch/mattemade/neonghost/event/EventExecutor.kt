package io.itch.mattemade.neonghost.event

import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.CharacterAnimations
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.character.rei.Player
import io.itch.mattemade.neonghost.tempo.UI
import io.itch.mattemade.neonghost.world.CameraMan
import io.itch.mattemade.neonghost.world.Trigger

class EventExecutor(
    private val assets: Assets,
    private val player: Player,
    private val ui: UI,
    private val cameraMan: CameraMan,
    private val eventState: MutableMap<String, Int>,
    private val spawnEnemy: (EnemySpec) -> Unit,
    private val onTrigger: (String) -> Unit,
    private val onEventFinished: () -> Unit
) {

    private var activeEventName: String = ""
    private var activeEvent: List<String>? = null
    private var activeEventPosition = 0
    private var currentChoice = mutableSetOf<String>()
    private var currentRequirements = mutableSetOf<String>()
    var isFighting = false
        private set
    val isInDialogue: Boolean
        get() = activeEvent != null && !isFighting

    fun execute(trigger: Trigger) {
        activeEventName = trigger.name
        val state = eventState[activeEventName] ?: 0
        activeEvent = trigger.properties[state.toString()]?.string?.lines()
        activeEventPosition = 0
        executeItem()
    }

    fun advance() {
        if (isFighting) {
            isFighting = false
            makeCameraFollowPlayer()
        }
        activeEventPosition++
        if (activeEventPosition == (activeEvent?.size ?: 0)) {
            activeEventPosition = 0
            activeEvent = null
            currentChoice.clear()
            currentRequirements.clear()
            ui.stopDialog()
            onEventFinished()
        } else {
            executeItem()
        }
    }

    fun selectOption(key: String) {
        currentChoice += key
        ui.hideOptions()
        advance()
    }

    private fun executeItem() {
        val item = activeEvent?.get(activeEventPosition)?.trim() ?: return
        println("executing $item")
        if (item.isBlank() || item.startsWith('#')) {
            advance()
            return
        }
        val split = item.split(" ", limit = 2).map { it.trim() }
        val subject = split[0]
        val action = split.getOrNull(1)
        when (subject) {
            "player" -> action.checkingChoice().executeForPlayer()
            "fight" -> action.checkingChoice().executeFight()
            "state" -> action.checkingChoice().executeState()
            "trigger" -> action.checkingChoice().executeTrigger()
            "camera" -> action.checkingChoice().executeCamera()
            "choose" -> action.checkingChoice().executeChoose()
            "choice" -> action.updateRequirements()
            else -> item.checkingChoice().executeDialogueLine()
        }
    }

    private fun String?.checkingChoice(): String? =
        takeIf { currentRequirements.isEmpty() || currentChoice.containsAll(currentRequirements) }

    private fun String?.executeForPlayer() {
        when (this) {
            "stop" -> player.stopBody(resetAnimationToIdle = true)
        }
        advance()
    }

    private fun String?.executeFight() {
        if (this == null) {
            advance()
            return
        }
        this.split(",").forEach {
            when (it.trim().lowercase()) {
                "punk" -> spawnEnemy(EnemySpec(assets.animation.punkAnimations, 1f, 5))
                "guard" -> spawnEnemy(EnemySpec(assets.animation.guardAnimations, 3f, 8))
                "officer" -> spawnEnemy(EnemySpec(assets.animation.officerAnimations, 5f, 20))
            }
        }
        isFighting = true
        ui.stopDialog()
        lockCamera()
    }

    private fun String?.executeState() {
        if (this == null) {
            advance()
            return
        }
        eventState[activeEventName] = this.toInt()
        advance()
    }

    private fun String?.executeTrigger() {
        if (this != null) {
            onTrigger(this)
        }
        advance()
    }

    private fun String?.executeChoose() {
        if (this.isNullOrBlank()) {
            advance()
            return
        }
        val options = this.split(";").map {
            val split = it.split(":", limit = 2)
            split[0].trim() to split[1].trim()
        }
        ui.showOptions(options)
    }

    private fun String?.updateRequirements() {
        currentRequirements.clear()
        if (this != null) {
            currentRequirements.addAll(this.split(",").map { it.trim() })
        }

        advance()
    }

    private fun String?.executeCamera() {
        if (this == null) {
            advance()
            return
        }
        when (this) {
            "lock" -> lockCamera()
            "follow" -> makeCameraFollowPlayer()
        }
    }

    private fun makeCameraFollowPlayer() {
        cameraMan.lookAt(withinSeconds = 2f) { it.set(player.x, Game.visibleWorldHeight / 2f) }
    }

    private fun String?.executeDialogueLine() {
        if (this.isNullOrBlank()) {
            advance()
            return
        }
        // TODO: ask UI to show dialogue line
        val isLeft = this.contains("<:")
        val (portrait, text) = this.split("<:", ">:", limit = 2)
        ui.showDialogLine(portrait, isLeft, text)
        player.stopBody(resetAnimationToIdle = true)
        // TODO: when UI finishes showing dialogue line, it should call back "advance()"
    }

    private fun lockCamera() {
        cameraMan.lookAt(withinSeconds = 0f, null)
    }


    data class EnemySpec(
        val characterAnimations: CharacterAnimations,
        val difficulty: Float,
        val health: Int
    )
}