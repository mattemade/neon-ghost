package io.itch.mattemade.neonghost.event

import com.littlekt.graphics.g2d.tilemap.tiled.TiledObjectLayer
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.CharacterAnimations
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.LevelSpec
import io.itch.mattemade.neonghost.character.rei.Player
import io.itch.mattemade.neonghost.tempo.UI
import io.itch.mattemade.neonghost.world.CameraMan
import io.itch.mattemade.neonghost.world.Trigger
import io.itch.mattemade.neonghost.world.Wall
import kotlin.random.Random

class EventExecutor(
    private val assets: Assets,
    private val player: Player,
    private val ui: UI,
    private val cameraMan: CameraMan,
    private val levelSpec: LevelSpec,
    private val eventState: MutableMap<String, Int>,
    private val interactionOverride: MutableMap<String, String>,
    private val spawnEnemy: (EnemySpec) -> Unit,
    private val onTrigger: (String) -> Unit,
    private val onEventFinished: (Trigger) -> Unit,
    private val onRemember: (String) -> Unit,
    private val onForget: (String) -> Unit,
    private val onTeleport: (door: String, toRoom: String) -> Unit,
    private val onMusic: (String) -> Unit,
    private val onScreen: (String) -> Unit,
    private val onSave: () -> Unit,
    private val wait: (Float) -> Unit,
) {

    private var activeTrigger: Trigger? = null
    private var activeEventName: String = ""
    private var activeEvent: List<String>? = null
    private var activeEventPosition = 0
    private var currentChoice = mutableSetOf<String>()
    private var currentRequirements = mutableSetOf<String>()
    private var negativeRequirements = mutableSetOf<String>()
    var isFighting = false
        private set
    val isInDialogue: Boolean
        get() = activeEvent != null && !isFighting

    fun execute(trigger: Trigger, knowledge: Set<String>) {
        activeTrigger = trigger
        activeEventName = trigger.name
        val state = eventState[activeEventName] ?: 0
        activeEvent = trigger.properties[state.toString()]?.string?.lines()
        activeEventPosition = 0
        currentChoice.addAll(knowledge)
        executeItem()
    }

    fun advance(forceEnd: Boolean = false) {
        if (isFighting) {
            isFighting = false
            makeCameraFollowPlayer()
        }
        activeEventPosition++
        if (forceEnd || activeEventPosition == (activeEvent?.size ?: 0)) {
            activeEventPosition = 0
            activeEvent = null
            currentChoice.clear()
            currentRequirements.clear()
            ui.stopDialog()
            activeTrigger?.let {
                onEventFinished(it)
                activeTrigger = null
            }
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
            "not" -> action.updateNegativeRequirements()
            "remember" -> action.checkingChoice().executeRemember()
            "forget" -> action.checkingChoice().executeForget()
            "rename" -> action.checkingChoice().executeRename()
            "teleport" -> action.checkingChoice().executeTeleport()
            "music" -> action.checkingChoice().executeMusic()
            "screen" -> action.checkingChoice().executeScreen()
            "wait" -> action.checkingChoice().executeWait()
            "save" -> executeSave()
            "stop" -> executeStopDialog()
            "end" -> advance(forceEnd = requirementsFulfilled())
            else -> item.checkingChoice().executeDialogueLine()
        }
    }

    private fun String?.checkingChoice(): String? =
        takeIf { requirementsFulfilled() }

    private fun requirementsFulfilled(): Boolean =
        (currentRequirements.isEmpty() || currentChoice.containsAll(currentRequirements))
                && (negativeRequirements.isEmpty() || !currentChoice.containsAll(
            negativeRequirements
        ))

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

        activeTrigger?.let { trigger ->
            this.split(",").forEach {
                var spec = it.trim().lowercase()
                var fromLeft = Random.nextBoolean()
                var verticalPositionRate = Random.nextFloat()
                if (spec.contains('<')) {
                    fromLeft = true
                    val optionalPosition = spec.substringAfter('<')
                    if (optionalPosition.isNotBlank()) {
                        verticalPositionRate = optionalPosition.toFloat()
                    }
                    spec = spec.substringBefore('<')
                } else if (spec.contains('>')) {
                    fromLeft = false
                    val optionalPosition = spec.substringAfter('>')
                    if (optionalPosition.isNotBlank()) {
                        verticalPositionRate = optionalPosition.toFloat()
                    }
                    spec = spec.substringBefore('>')
                }

                val verticalPosition = trigger.bottom + verticalPositionRate * trigger.height

                enemySpec(spec, fromLeft, verticalPosition)?.let(spawnEnemy)
            }
            isFighting = true
            ui.stopDialog()
            lockCamera()
        } ?: run {
            advance()
        }

    }

    fun enemySpec(
        spec: String,
        fromLeft: Boolean,
        verticalPosition: Float,
        name: String? = null
    ): EnemySpec? =
        when (spec) {
            "punk" ->
                EnemySpec(
                    assets.animation.punkAnimations,
                    1f,
                    5,
                    fromLeft,
                    verticalPosition,
                    name,
                )
            "guard" ->
                EnemySpec(
                    assets.animation.guardAnimations,
                    3f,
                    8,
                    fromLeft,
                    verticalPosition,
                    name,
                )
            "officer" ->
                EnemySpec(
                    assets.animation.officerAnimations,
                    5f,
                    20,
                    fromLeft,
                    verticalPosition,
                    name,
                )
            else -> null
        }

    private fun String?.executeState() {
        if (this == null) {
            advance()
            return
        }
        val stateArgs = this.split(" ", limit = 2)
        val state = stateArgs[0].trim().toInt()
        val optionalEventName = stateArgs.getOrNull(1)?.trim()
        eventState[optionalEventName ?: activeEventName] = state
        advance()
    }

    private fun String?.executeTrigger() {
        if (this != null) {
            onTrigger(this)
        } else {
            advance()
        }
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

    private fun String?.updateNegativeRequirements() {
        negativeRequirements.clear()
        if (this != null) {
            negativeRequirements.addAll(this.split(",").map { it.trim() })
        }
        advance()
    }

    private fun String?.executeCamera() {
        when (this) {
            "lock" -> lockCamera()
            "trigger" -> makeCameraFollowTrigger()
            "follow" -> makeCameraFollowPlayer()
            else -> makeCameraFollowObject(this)
        }
        advance()
    }

    private fun String?.executeRemember() {
        if (this != null) {
            onRemember(this)
        }
        advance()
    }

    private fun String?.executeForget() {
        if (this != null) {
            onForget(this)
        }
        advance()
    }

    private fun String?.executeRename() {
        if (this != null) {
            val (key, name) = this.split(":", limit = 2)
            interactionOverride[key] = name.uppercase()
        }
        advance()
    }

    private fun String?.executeTeleport() {
        if (this == null) {
            advance()
            return
        }
        val doorName = activeTrigger?.name
        advance(forceEnd = true)
        if (doorName != null) {
            onTeleport(doorName, this)
        }
    }

    private fun String?.executeMusic() {
        if (this == null) {
            advance()
            return
        }
        onMusic(this)
        advance()
    }

    private fun String?.executeScreen() {
        if (this == null) {
            advance()
            return
        }
        onScreen(this)
        //advance()
    }

    private fun executeSave() {
        if (requirementsFulfilled()) {
            onSave()
        }
        advance()
    }

    private fun executeStopDialog() {
        if (!requirementsFulfilled()) {
            advance()
            return
        }
        ui.stopDialog()
        advance()
    }

    private fun String?.executeWait() {
        if (this == null) {
            advance()
            return
        }
        toFloatOrNull()?.let(wait) ?: run {
            advance()
        }
    }

    private fun makeCameraFollowTrigger() {
        activeTrigger?.let { trigger ->
            cameraMan.lookAt(withinSeconds = 2f) { it.set(trigger.centerX, trigger.centerY) }
        }
    }

    private fun makeCameraFollowPlayer() {
        cameraMan.lookAt(withinSeconds = 2f) {
            it.set(
                player.x,
                if (levelSpec.freeCameraY) player.y - Game.visibleWorldHeight / 8f else Game.visibleWorldHeight / 2f
            )
        }
    }

    private fun makeCameraFollowObject(s: String?) {
        levelSpec.level.layers.asSequence().filterIsInstance<TiledObjectLayer>().forEach {
            if (it.name == "trigger") {
                it.objects.forEach { obj ->
                    if (obj.name == s) {
                        val mapHeight = levelSpec.level.height * levelSpec.level.tileHeight
                        cameraMan.lookAt(withinSeconds = 2f) {
                            it.set(obj.bounds.x + obj.bounds.width / 2f, mapHeight - (obj.bounds.y - obj.bounds.height / 2f)).mulLocal(Game.IPPU)
                        }
                        return
                    }
                }
            }
        }
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
        val health: Int,
        val fromLeft: Boolean,
        val verticalPosition: Float,
        val name: String? = null
    )
}