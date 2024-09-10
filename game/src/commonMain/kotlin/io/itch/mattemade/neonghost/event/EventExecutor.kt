package io.itch.mattemade.neonghost.event

import com.littlekt.audio.AudioClipEx
import com.littlekt.graphics.g2d.tilemap.tiled.TiledObjectLayer
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.CharacterAnimations
import io.itch.mattemade.neonghost.ExtraAssets
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.LevelSpec
import io.itch.mattemade.neonghost.character.rei.Player
import io.itch.mattemade.neonghost.tempo.UI
import io.itch.mattemade.neonghost.world.CameraMan
import io.itch.mattemade.neonghost.world.Trigger
import kotlin.random.Random
class EventExecutor(
    private val assets: Assets,
    private val extraAssets: ExtraAssets,
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
    private val onSound: (String, Float) -> Int,
    private val onScreen: (String, Float?) -> Unit,
    private val onSave: (trigger: String?) -> Unit,
    private val wait: (Float) -> Unit,
    private val onClearQueue: () -> Unit,
) {

    private var activeTrigger: Trigger? = null
    private var activeEventName: String = ""
    private var activeEvent: List<String>? = null
    private var activeEventPosition = 0
    private var currentChoice = mutableSetOf<String>()
    private var currentRequirements = mutableSetOf<String>()
    private var anyOfRequirementsIsEnough = false
    private var negativeRequirements = mutableSetOf<String>()
    var isFighting = false
        private set
    val isInDialogue: Boolean
        get() = activeEvent != null && !isFighting
    private var skipSpeechOnNextText = false

    private var keepPlaying: Int = 0
    private var currentPlayingSound: AudioClipEx? = null
    private var currentPlayingSoundId: Int = -1

    fun execute(trigger: Trigger, knowledge: Set<String>) {
        activeTrigger = trigger
        activeEventName = trigger.name
        val state = eventState[activeEventName] ?: 0
        activeEvent = trigger.properties[state.toString()]?.string?.lines()
        activeEventPosition = 0
        currentChoice.addAll(knowledge)
        negativeRequirements.clear()
        executeItem()
    }

    fun advance(forceEnd: Boolean = false) {
        if (keepPlaying > 0) {
            keepPlaying--
        } else if (currentPlayingSound != null) {
            currentPlayingSound?.stop(currentPlayingSoundId)
            currentPlayingSoundId = -1
            currentPlayingSound = null
        }
        if (isFighting) {
            isFighting = false
            makeCameraFollowPlayer(null)
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
            "or" -> action.updateRequirements(anyEnough = true)
            "not" -> action.updateNegativeRequirements()
            "remember" -> action.checkingChoice().executeRemember()
            "forget" -> action.checkingChoice().executeForget()
            "rename" -> action.checkingChoice().executeRename()
            "teleport" -> action.checkingChoice().executeTeleport()
            "music" -> action.checkingChoice().executeMusic()
            "sound" -> action.checkingChoice().executeSound(0.5f)
            "voice" -> action.checkingChoice().executeSound(4f)
            "screen" -> action.checkingChoice().executeScreen()
            "wait" -> action.checkingChoice().executeWait()
            "keepPlaying" -> action.checkingChoice().executeKeepPlaying()
            "clearQueue" -> executeClearQueue()
            "save" -> executeSave()
            "stop" -> executeStopDialog()
            "playSpeechNext" -> executePlaySpeechNext()
            "end" -> advance(forceEnd = requirementsFulfilled())
            else -> item.checkingChoice().executeDialogueLine()
        }
    }

    private fun String?.checkingChoice(): String? =
        takeIf { requirementsFulfilled() }

    private fun requirementsFulfilled(): Boolean {
        val allCurrentFulfilled = currentRequirements.isEmpty() || currentChoice.containsAll(currentRequirements)
        val anyCurrentIsEnough = anyOfRequirementsIsEnough && currentChoice.any(currentRequirements::contains)
        val allNegativeFulfilled = negativeRequirements.isEmpty() || !currentChoice.any(negativeRequirements::contains)
        return (allCurrentFulfilled || anyCurrentIsEnough)
                && allNegativeFulfilled
    }


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
            lockCamera(null)
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
                    4f,
                    16,
                    fromLeft,
                    verticalPosition,
                    name,
                )

            "officer2" ->
                EnemySpec(
                    assets.animation.officerAnimations,
                    10f,
                    10000,
                    fromLeft,
                    verticalPosition,
                    name,
                )

            "dummy" ->
                EnemySpec(
                    assets.animation.dummyAnimations,
                    1f,
                    Int.MAX_VALUE,
                    fromLeft,
                    verticalPosition,
                    "dummy",
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

    private fun String?.updateRequirements(anyEnough: Boolean = false) {
        anyOfRequirementsIsEnough = anyEnough
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
        if (this == null) {
            advance()
            return
        }
        val args = this.split(":", limit = 2)
        val command = args[0]
        val delay = args.getOrNull(1)?.toFloatOrNull()

        when (command) {
            "lock" -> lockCamera(delay)
            "trigger" -> makeCameraFollowTrigger(delay)
            "follow" -> makeCameraFollowPlayer(delay)
            else -> makeCameraFollowObject(command, delay)
        }
        advance()
    }

    private fun String?.executeRemember() {
        if (this != null) {
            onRemember(this)
            if (this == "lift_hacked") {
                "Hacking".executeSound(volume = 0.5f)
                return
            }
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

    private fun String?.executeSound(volume: Float) {
        if (this == null) {
            advance()
            return
        }
        skipSpeechOnNextText = true
        advance()
        if (extraAssets.isLoaded) {
            currentPlayingSound = extraAssets.sound.concurrentClips[this]
            currentPlayingSoundId = onSound(this, volume)
        }
    }

    private fun String?.executeScreen() {
        if (this == null) {
            advance()
            return
        }
        val args = this.split(":", limit = 2)
        val command = args[0]
        val delay = args.getOrNull(1)?.toFloatOrNull()
        onScreen(command, delay)
        //advance()
    }

    private fun executeSave() {
        if (requirementsFulfilled()) {
            onSave(activeTrigger?.name)
        }
        advance()
    }

    private fun executePlaySpeechNext() {
        if (requirementsFulfilled()) {
            skipSpeechOnNextText = false
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

    private fun String?.executeKeepPlaying() {
        if (this == null) {
            advance()
            return
        }
        toIntOrNull()?.let {
            keepPlaying = it
        }
        advance()
    }

    private fun executeClearQueue() {
        if (requirementsFulfilled()) {
            onClearQueue()
        }
        advance()
    }

    private fun makeCameraFollowTrigger(delay: Float?) {
        activeTrigger?.let { trigger ->
            cameraMan.lookAt(withinSeconds = delay ?: 2f) {
                it.set(
                    trigger.centerX,
                    trigger.centerY
                )
            }
        }
    }

    private fun makeCameraFollowPlayer(delay: Float?) {
        cameraMan.lookAt(withinSeconds = delay ?: 2f) {
            it.set(
                player.x,
                if (levelSpec.freeCameraY) player.y - Game.visibleWorldHeight / 8f else Game.visibleWorldHeight / 2f
            )
        }
    }

    private fun makeCameraFollowObject(s: String?, delay: Float?) {
        levelSpec.level.layers.asSequence().filterIsInstance<TiledObjectLayer>().forEach {
            if (it.name == "trigger") {
                it.objects.forEach { obj ->
                    if (obj.name == s) {
                        val mapHeight = levelSpec.level.height * levelSpec.level.tileHeight
                        cameraMan.lookAt(withinSeconds = delay ?: 2f) {
                            it.set(
                                obj.bounds.x + obj.bounds.width / 2f,
                                mapHeight - (obj.bounds.y - obj.bounds.height / 2f)
                            ).mulLocal(Game.IPPU)
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
        ui.showDialogLine(portrait, isLeft, text, skipSpeechOnNextText)
        skipSpeechOnNextText = false
        player.stopBody(resetAnimationToIdle = true)
        // TODO: when UI finishes showing dialogue line, it should call back "advance()"
    }

    private fun lockCamera(delay: Float?) {
        cameraMan.lookAt(withinSeconds = delay ?: 0f, null)
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

