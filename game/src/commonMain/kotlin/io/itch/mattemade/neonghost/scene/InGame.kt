package io.itch.mattemade.neonghost.scene

import com.littlekt.Context
import com.littlekt.graphics.Camera
import com.littlekt.graphics.Color
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.graphics.g2d.tilemap.tiled.TiledObjectLayer
import com.littlekt.graphics.g2d.tilemap.tiled.TiledTilesLayer
import com.littlekt.graphics.shader.ShaderProgram
import com.littlekt.graphics.toFloatBits
import com.littlekt.input.InputMapController
import com.littlekt.math.MutableVec2f
import com.littlekt.math.PI2
import com.littlekt.math.Vec2f
import com.littlekt.math.geom.radians
import com.littlekt.util.datastructure.BiMap
import com.littlekt.util.milliseconds
import com.littlekt.util.seconds
import com.soywiz.kds.fastCastTo
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.ExtraAssets
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.Game.Companion.IPPU
import io.itch.mattemade.neonghost.Game.Companion.visibleWorldHeight
import io.itch.mattemade.neonghost.Game.Companion.visibleWorldWidth
import io.itch.mattemade.neonghost.LevelSpec
import io.itch.mattemade.neonghost.character.DepthBasedRenderable
import io.itch.mattemade.neonghost.character.VisibleObject
import io.itch.mattemade.neonghost.character.enemy.Enemy
import io.itch.mattemade.neonghost.character.enemy.EnemyAi
import io.itch.mattemade.neonghost.character.rei.NeonGhost
import io.itch.mattemade.neonghost.character.rei.Player
import io.itch.mattemade.neonghost.event.EventExecutor
import io.itch.mattemade.neonghost.shader.ParticleFragmentShader
import io.itch.mattemade.neonghost.shader.ParticleVertexShader
import io.itch.mattemade.neonghost.shader.Particler
import io.itch.mattemade.neonghost.tempo.Choreographer
import io.itch.mattemade.neonghost.tempo.UI
import io.itch.mattemade.neonghost.world.CameraMan
import io.itch.mattemade.neonghost.world.GeneralContactListener
import io.itch.mattemade.neonghost.world.GhostBody
import io.itch.mattemade.neonghost.world.Trigger
import io.itch.mattemade.neonghost.world.Wall
import io.itch.mattemade.utils.math.belongsToEllipse
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import io.itch.mattemade.utils.render.PixelRender
import io.itch.mattemade.utils.render.createPixelFrameBuffer
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.World
import kotlin.time.Duration

class InGame(
    private val context: Context,
    private val assets: Assets,
    private val extraAssets: ExtraAssets,
    private val particleShader: ShaderProgram<ParticleVertexShader, ParticleFragmentShader>,
    private val levelSpec: LevelSpec,
    private val inputController: InputMapController<GameInput>,
    private val choreographer: Choreographer,
    private val ghostOverlay: GhostOverlay,
    private val eventState: MutableMap<String, Int>,
    private val playerKnowledge: MutableSet<String>,
    private val interactionOverride: MutableMap<String, String>,
    private val onGameOver: () -> Unit,
    private val goThroughDoor: (door: String, toRoom: String, playerHealth: Int, isMagic: Boolean, deaths: Int) -> Unit,
    private val wentThroughDoor: String? = null,
    private val saveState: (fromTrigger: String?) -> Unit,
    private val restartGame: () -> Unit,
    private val playerHealth: Int,
    private val isMagic: Boolean,
    private val deaths: Int,
) : Releasing by Self() {

    private var initialized = false
    private val level = levelSpec.level
    private val sharedFrameBuffer =
        context.createPixelFrameBuffer(Game.virtualWidth, Game.virtualHeight)
    private val worldRender = PixelRender(
        context,
        sharedFrameBuffer,
        Game.visibleWorldWidth,
        Game.visibleWorldHeight,
        ::updateWorld,
        ::renderWorld,
        clear = true
    ).releasing()
    private val uiRenderer = PixelRender(
        context,
        sharedFrameBuffer,
        Game.virtualWidth,
        Game.virtualHeight,
        ::updateUi,
        ::renderUi
    ).releasing()
    val texture = sharedFrameBuffer.textures[0]

    private val tiledLayers = level.layers.asSequence().filterIsInstance<TiledTilesLayer>()
    private val backgroundLayer = tiledLayers.firstOrNull { it.name == "background_p0" }
    private val floorLayer = tiledLayers.first { it.name == "floor" }
    private val wallLayer = tiledLayers.first { it.name == "wall" }
    private val decorationLayer = tiledLayers.first { it.name == "decoration" }
    private val decorationLayer2 = tiledLayers.firstOrNull() { it.name == "decoration2" }
    private val foregroundLayer = tiledLayers.first { it.name == "foreground" }
    private var notAdjustedTime = 0f
    private var notAdjustedDt: Duration = Duration.ZERO
    private val tempColor = MutableColor()

    private val ui by lazy {
        UI(
            context,
            assets,
            player,
            choreographer,
            inputController,
            interactionOverride,
            ::advanceDialog,
            ::activateInteraction,
            ::selectOption,
            player::isMagicGirl,
            canAct = {
                timedActions.isEmpty() && dream == null && transformation == null && ghostFromPowerPlant == null
            },
            canInteract = {
                timedActions.isEmpty() && !isInDialogue && enemies.isEmpty()
            }
        )
    }

    private val eventExecutor by lazy {
        EventExecutor(
            assets,
            extraAssets,
            player,
            ui,
            cameraMan,
            levelSpec,
            eventState,
            interactionOverride,
            ::createEnemy,
            ::onTriggerEventCallback,
            ::onEventFinished,
            playerKnowledge::add,
            playerKnowledge::remove,
            onTeleport = ::openDoor,
            onMusic = ::musicCommand,
            onSound = ::soundCommand,
            onScreen = ::screenCommand,
            onSave = saveState,
            wait = ::wait,
            onClearQueue = ::clearQueue
        )
    }

    private fun openDoor(door: String, toRoom: String) {
        val finalFinalRoom = toRoom == "washing_room_3"
        val finalRoom = (toRoom == "washing_room_2" || finalFinalRoom) && !playerKnowledge.contains(
            "lastFlash"
        )
        if (finalRoom) {
            if (finalFinalRoom) {
                ui.setFadeEverythingColor(Color.WHITE)
            } else {
                ui.setFadeWorldColor(Color.WHITE)
            }
        } else {
            ui.setFadeWorldColor(Color.BLACK)
        }
        initialized = false
        ui.availableInteraction = null
        if (finalFinalRoom) {
            ui.setFadeEverything(0f)
        } else {
            ui.setFadeWorld(0f)
        }
        timedActions.add(TimedAction(0.5f, {
            if (finalFinalRoom) {
                ui.setFadeEverything(1f - it)
            } else {
                ui.setFadeWorld(1f - it)
            }
        }, {
            if (finalFinalRoom) {
                ui.setFadeEverything(1f)
            } else {
                ui.setFadeWorld(1f)
            }
            if (finalRoom) {
                ghostOverlay.finalSequence()
            }
            goThroughDoor(door, toRoom, player.health, player.isMagicGirl, deaths)
        }))
    }

    private val isInDialogue: Boolean get() = eventExecutor.isInDialogue || timedActions.isNotEmpty()
    private fun advanceDialog() {
        eventExecutor.advance()
    }

    private fun activateInteraction(trigger: Trigger) {
        eventExecutor.execute(trigger, playerKnowledge)
    }

    private fun selectOption(key: String) {
        eventExecutor.selectOption(key)
    }

    private val timedActions = mutableListOf<TimedAction>()
    private fun wait(time: Float) {
        //timedActions.clear()
        timedActions += TimedAction(time, {}, eventExecutor::advance)
    }

    private fun clearQueue() {
        timedActions.clear()
    }

    private var probablyReturnPreviousMusicIfNotAskedForOther: Boolean = false
    private fun musicCommand(command: String) {
        probablyReturnPreviousMusicIfNotAskedForOther = false
        if (command == "previous") {
            choreographer.previousMusic?.let {
                val track = assets.music.concurrentTracks[it]
                    ?: if (extraAssets.isLoaded) extraAssets.music.concurrentTracks[it] else null
                track?.let(choreographer::play)
            }

        } else {
            val track = assets.music.concurrentTracks[command]
                ?: if (extraAssets.isLoaded) extraAssets.music.concurrentTracks[command] else null
            track?.let { choreographer.play(it) }
        }
    }

    private fun soundCommand(command: String, volume: Float): Int {
        if (!extraAssets.isLoaded) {
            return -1
        }
        return when (command) {
            else -> extraAssets.sound.concurrentClips[command]?.let {
                choreographer.uiSound(it, volume = volume)
            }
        } ?: -1
    }

    private fun screenCommand(command: String, delay: Float?) {
        when (command) {
            "fadeOut" -> scheduleFadeOut(length = delay ?: 0.5f) { eventExecutor.advance() }
            "fadeIn" -> scheduleFadeIn(length = delay ?: 0.5f) { eventExecutor.advance() }
            "splashOut" -> scheduleFadeOut(
                colorComponent = 1f,
                length = delay ?: 0.1f
            ) { eventExecutor.advance() }

            "splashIn" -> {
                scheduleFadeIn(colorComponent = 1f, length = delay ?: 2f) {
                    eventExecutor.advance()
                }
            }

            "slowFadeIn" -> scheduleFadeIn(
                maxValue = 0.8f,
                length = delay ?: 1f
            ) { eventExecutor.advance() }

            "slowFadeOut" -> scheduleFadeOut(
                maxValue = 0.8f,
                length = delay ?: 1f
            ) { eventExecutor.advance() }
        }
    }


    private val levelHeight by lazy { level.height * level.tileHeight }
    private val walls = mutableListOf<Wall>()
    private val world by lazy {
        World(gravityX = 0f, gravityY = 0f).apply {
            val mapHeight = levelHeight
            level.layers.asSequence().filterIsInstance<TiledObjectLayer>().forEach {
                if (it.name == "walls") {
                    it.objects.forEach {
                        walls += Wall(this, it.x, it.y, it.shape, mapHeight)
                    }
                }
            }
            setContactListener(GeneralContactListener(::enterTrigger, ::exitTrigger))
        }.registerAsContextDisposer(Body::class) {
            destroyBody(it as Body)
        }
    }
    private val neonWorld by lazy { World(gravityX = 0f, gravityY = 0f) }
    private val playerSpawnPosition by lazy {
        val placement =
            wentThroughDoor?.let { door ->
                level.layer("trigger")
                    .fastCastTo<TiledObjectLayer>().objects.first { it.name == door }
            } ?: level.layer("spawn").fastCastTo<TiledObjectLayer>().objects.first()
        val x = placement.bounds.x + placement.bounds.width / 2f
        val y = levelHeight - (placement.bounds.y - placement.bounds.height / 2f)
        Vec2(x * Game.IPPU, y * Game.IPPU)
    }
    private val player by lazy {
        Player(
            playerSpawnPosition,
            world,
            choreographer,
            assets,
            extraAssets,
            inputController,
            assets.objects.particleSimulator,
            context.vfs,
            isMagicGirl = isMagic,
            initialHealth = playerHealth,
            canAct = { !isInDialogue },
            gameOver = ::gameOver,
            changePlaybackRateExternal = ::changePlaybackRate,
            spawnNeonGhost = ::spawnNeonGhost,
            castAoe = ::castAoe,
            castProjectile = ::castProjectile,
            spawnParticles = ::spawnParticles,
            canPunch = ::canPunch,
            updateEnemyAi = ::updateEnemyAi,
            resetEnemyAi = ::resetEnemyAi,
            magicTutorial = ::isCastingTutorial,
        )
    }

    private fun canPunch(): Boolean = enemies.isNotEmpty() || ui.availableInteraction == null
    private fun updateEnemyAi() {
        enemyAi.update()
    }

    private fun resetEnemyAi() {
        enemyAi.update()
    }

    private fun isCastingTutorial(): Boolean {
        val state = eventState["tutorial_trigger"] ?: 0
        return state > 0 && state < 5
    }

    private var neonGhost: NeonGhost? = null
    private var addNeonGhostToList = false
    private var removeNeonGhostFromList = false
    private fun spawnNeonGhost(facingLeft: Boolean) {
        neonGhost = NeonGhost(
            Vec2(player.x, player.y),
            facingLeft,
            neonWorld,
            assets,
            inputController,
            assets.objects.particleSimulator,
            context.vfs,
            ghostOverlay,
            cameraMan,
            removeGhost = ::removeNeonGhost,
            castAoe = ::castAoe,
            castProjectile = ::castProjectile,
        )
        addNeonGhostToList = true
    }

    private fun removeNeonGhost(ghost: NeonGhost) {
        removeNeonGhostFromList = true
        ghost.release()
    }

    private fun changePlaybackRate(rate: Float) {
        choreographer.setPlaybackRate(rate)
    }

    private fun castAoe(position: Vec2) {
        castAoe(position, Player.castsToStopTime)
    }

    private fun castAoe(position: Vec2, power: Int) {
        if (eventState["tutorial_trigger"] == 1) {
            eventState["tutorial_trigger"] = 2
            triggers.get("tutorial_trigger")?.let {
                eventExecutor.execute(it, playerKnowledge)
            }
        }
        enemies.forEach {
            if (belongsToEllipse(
                    it.x,
                    it.y,
                    position.x,
                    position.y,
                    Player.spellRx,
                    Player.spellRy,
                    it.extraForEllipseCheck,
                )
            ) {
                it.hit(position, power * 2 + 1, fromSpell = true)
            }
        }
    }

    // for the Neon Ghost
    private fun castProjectile(position: Vec2, facingLeft: Boolean) {
        castProjectile(position, Player.castsToStopTime, facingLeft)
    }

    private var closeTutorialOnNextUpdate = false
    private fun castProjectile(position: Vec2, power: Int, facingLeft: Boolean) {
        println("casting projectile at $position with $power, isLeft = $facingLeft")
        if (eventState["tutorial_trigger"] == 3) {
            eventState["tutorial_trigger"] = 4

            // since projectile casts by Action button, if we run the trigger now,
            // it will receive Action button too, advancing the dialogue
            closeTutorialOnNextUpdate = true

        }
    }

    private val particlersToAdd = mutableListOf<Particler>()
    private val particlersToRemove = mutableListOf<Particler>()
    private fun spawnParticles(
        depth: Float, instances: Int, lifeTime: Float,
        fillData: (
            index: Int,
            startColor: FloatArray,
            endColor: FloatArray,
            startPosition: FloatArray,
            endPosition: FloatArray,
            activeBetween: FloatArray
        ) -> Unit,
    ) {
        particlersToAdd += Particler(
            context,
            particleShader,
            depth,
            instances,
            lifeTime,
            Game.IPPU * 2f,
            2,
            fillData
        ) {
            particlersToRemove += it
        }
    }

    private val depthBasedDrawables by lazy {
        mutableListOf<DepthBasedRenderable>(
            player,
            /*enemy1,
            enemy2*/
        )
    }
    private val enemies = mutableListOf<Enemy>()
    private val staticEnemies = mutableListOf<Enemy>()
    private val deadEnemies = mutableListOf<Enemy>()
    private val enemyAi by lazy {
        EnemyAi(player, enemies)
    }

    private val cameraMan by lazy {
        val tempVec2 = Vec2(
            playerSpawnPosition.x,
            if (levelSpec.freeCameraY) playerSpawnPosition.y - Game.visibleWorldHeight / 8f else visibleWorldHeight / 2f
        )
        CameraMan(context, choreographer, world, tempVec2).apply {
            lookAt {
                it.set(
                    player.x,
                    if (levelSpec.freeCameraY) player.y - Game.visibleWorldHeight / 8f else visibleWorldHeight / 2f
                )
            }
        }
    }

    private val triggers: BiMap<String, Trigger> by lazy {
        val result = BiMap<String, Trigger>()
        level.layers.asSequence().filterIsInstance<TiledObjectLayer>()
            .first { it.name == "trigger" }.objects.forEach {
                // do not check for the state, as it can change in runtime
                /*val triggerState = eventState[it.name]
                if (triggerState == null || it.properties.containsKey(triggerState.toString())) {*/
                result.put(
                    it.name, Trigger(
                        world,
                        it.bounds,
                        levelHeight,
                        it.name,
                        it.properties
                    )
                )
                //}
            }
        result
    }


    private val worldStep = 1f / 60f
    private var worldAccumulator = 0f
    private var neonWorldAccumulator = 0f

    private var enterTriggers = mutableSetOf<Trigger>()
    private var exitTriggers = mutableSetOf<Trigger>()

    private fun onEventFinished(trigger: Trigger) {
        if (trigger.deactivated()) {
            ui.availableInteraction = null
        }
    }

    private var dream: Dream? = null
    private var waitForDisappear = false
    private var ghostFromPowerPlant: GhostFromPowerPlant? = null
    private var transformation: Transformation? = null
    private fun onTriggerEventCallback(event: String) {
        when (event) {
            "faster" -> {
                choreographer.setPlaybackRate(choreographer.playbackRate.toFloat() + 0.25f)
                eventExecutor.advance()
            }

            "slower" -> {
                choreographer.setPlaybackRate(choreographer.playbackRate.toFloat() - 0.25f)
                eventExecutor.advance()
            }

            "heal" -> {
                player.health = Player.maxPlayerHealth
                eventExecutor.advance()
            }

            "launchGhost" -> {
                ghostFromPowerPlant = GhostFromPowerPlant(
                    player,
                    context,
                    assets,
                    levelSpec,
                    inputController,
                    particleShader,
                ) {
                    ghostFromPowerPlant = null
                }
                timedActions += TimedAction(3f, {}) {
                    ghostOverlay.appear()
                    timedActions += TimedAction(4f, {}) {
                        eventExecutor.advance()
                    }
                }

                // activate particler from power plant
                // add timed action with delay to activate particler in ghost overlay
                // once finished activate ghost overlay
                /*ghostOverlay.activate()
                createGhostBody()
                eventExecutor.advance()*/
            }

            "transform" -> {
                ghostOverlay.blinking = true
                transformation = Transformation(
                    player,
                    context,
                    assets,
                    inputController,
                    particleShader
                ) {
                    transformation = null
                    player.transform()
                    eventExecutor.advance()
                }


            }

            "dream" -> {
                timedActions += TimedAction(9.5f, {}) {
                    if (extraAssets.isLoaded) {
                        choreographer.uiSound(
                            extraAssets.sound.concurrentClips["5_d"]!!,
                            volume = 4f
                        )
                    }
                }
                dream = Dream(
                    player,
                    context,
                    assets,
                    inputController,
                    particleShader
                ) {
                    dream = null
                    eventExecutor.advance()
                }
            }

            "restart" -> {
                restartGame()
            }

            "fullStop" -> {
                choreographer.fullStop()
                eventExecutor.advance()
            }

            "goodbye" -> {
                ghostOverlay.goodbye()
                eventExecutor.advance()
            }

            "waitForDisappear" -> {
                waitForDisappear = true
            }

            "activatePrepare" -> {
                player.showPrepare()
                eventExecutor.advance()
            }

            "activatePunch" -> {
                player.showPunch()
                scheduleFadeOut(length = 0.3f, colorComponent = 0.5f) {
                    eventExecutor.advance()
                }
            }

            "activateHandcuff" -> {
                player.showHandcuff()
                eventExecutor.advance()
            }

            "freeRei" -> {
                player.showFree()
                eventExecutor.advance()
            }
        }
    }

    private fun enterTrigger(trigger: Trigger) {
        enterTriggers.add(trigger)
    }

    private fun exitTrigger(trigger: Trigger) {
        if (enterTriggers.contains(trigger)) {
            enterTriggers.remove(trigger)
        } else {
            exitTriggers.add(trigger)
        }
    }

    private fun processTriggers() {
        /*
         TODO: there is a bug
         if player engages into a fight while standing on interaction point,
         it won't be cleared from the triggers list, even if player moves out of it
         to mitigate: do not place interaction points near fight triggers!!!!
         */
        if (eventExecutor.isInDialogue || eventExecutor.isFighting) {
            return
        }
        enterTriggers.forEach { triggger ->
            if (!triggger.deactivated()) {
                when (triggger.properties["type"]?.string) {
                    "trigger" -> eventExecutor.execute(triggger, playerKnowledge)
                    "interaction" -> ui.availableInteraction = triggger
                }
            }
        }
        enterTriggers.clear()
        exitTriggers.forEach { triggger ->
            when (triggger.properties["type"]?.string) {
                "interaction" -> ui.availableInteraction = null
            }
        }
        exitTriggers.clear()
    }

    private fun Trigger.deactivated(): Boolean {
        val eventState = eventState[name] ?: 0//return false
        val shouldBeRemoved = !properties.containsKey(eventState.toString())
        // do not remove triggers as they can be activated again
        /*if (shouldBeRemoved) {
            triggers.removeValue(this)
            release()
        }*/
        return shouldBeRemoved
    }

    private var ghostBody: GhostBody? = null
    private fun createGhostBody() {
        if (ghostBody == null) {
            ghostBody = GhostBody(world, ghostOverlay)
        }
    }

    private fun createEnemy(
        enemySpec: EventExecutor.EnemySpec,
        overrideX: Float? = null,
        overrideY: Float? = null,
    ): Enemy {
        // starting fight - should play fight music (won't do anything if it is already playing)
        if (overrideX == null && overrideY == null) {
            musicCommand("magical girl 3d")
        }

        val offsetX =
            if (enemySpec.fromLeft) -Game.visibleWorldWidth / 2f - 0.5f else Game.visibleWorldWidth / 2f + 0.5f
        val offsetY = enemySpec.verticalPosition
        val enemy = Enemy(
            enemySpec.name,
            Vec2(overrideX ?: (cameraMan.position.x + offsetX), overrideY ?: offsetY),
            player,
            world,
            choreographer,
            assets,
            enemySpec.characterAnimations,
            inputController,
            assets.objects.particleSimulator,
            context.vfs,
            difficulty = enemySpec.difficulty,
            initialHeath = enemySpec.health,
            initialFacingLeft = !enemySpec.fromLeft,
            canAct = { initialized && !isInDialogue && timedActions.isEmpty() },
            onDeath = ::onEnemyDeath,
            onBecomingAggessive = ::onEnemyBecomesAggressive
        )
        depthBasedDrawables.add(enemy)
        if (overrideX != null || overrideY != null) {
            staticEnemies.add(enemy)
        } else {
            if (staticEnemies.isNotEmpty()) {
                staticEnemies.forEach {
                    enemies.add(it)
                    ui.showHealth(it)
                    it.isAggressive = true
                }
                staticEnemies.clear()
            }
            enemies.add(enemy)
            ui.showHealth(enemy)
            enemy.isAggressive = true
        }
        return enemy
    }

    private fun onEnemyDeath(enemy: Enemy) {
        if (enemy.name != null) {
            eventState[enemy.name] = 1
        }
        deadEnemies += enemy
    }

    private fun onEnemyBecomesAggressive(enemy: Enemy) {
        println("became aggressive!!")
        musicCommand("magical girl 3d")
        staticEnemies.remove(enemy)
        enemies.add(enemy)
        ui.showHealth(enemy)
    }

    private fun gameOver() {
        scheduleGameOver()
    }

    private var time = 0f
    private var ghostCasting = 0f
    private var ghostStaying = 0f
    private var ghostCooldown = 0f
    private val ghostEdgeColor = MutableColor(1f, 1f, 1f, 0.9f).toFloatBits()
    private val ghostFillColor = MutableColor(1f, 1f, 1f, 0.75f).toFloatBits()

    private var music = false

    private fun initVisibleObjects() {
        level.layer("spawn").fastCastTo<TiledObjectLayer>().objects.forEach { obj ->
            val x = (obj.bounds.x + obj.bounds.width / 2f) * IPPU
            val topY = (levelHeight - obj.bounds.y) * IPPU
            val bottomY = (levelHeight - (obj.bounds.y - obj.bounds.height)) * IPPU
            val midY = (topY + bottomY) / 2f

            assets.texture.objects[obj.name]?.let { slice ->
                depthBasedDrawables.add(
                    VisibleObject(
                        position = Vec2f(x, bottomY),
                        slice = slice,
                        depth = midY
                    )
                )
            } ?: run {
                val name = obj.name
                if (eventState[name] != 1) {
                    val type = obj.properties["type"]?.string ?: "punk"
                    val facingLeft = obj.properties["facingLeft"]?.bool ?: false

                    eventExecutor.enemySpec(type, !facingLeft, 0f, name)?.let {
                        createEnemy(it, x, midY)
                    }
                }
            }
        }

    }

    init {
        if (playerKnowledge.contains("needToWaitABit")) {
            ui.setFadeEverythingColor(Color.BLACK)
            ui.setFadeEverything(1f)
            timedActions += TimedAction(2f, ui::setFadeEverything) { ui.setFadeEverything(0f) }
        } else {
            scheduleFadeIn(
                colorComponent = if (levelSpec.name.startsWith("washing_room_") && !playerKnowledge.contains(
                        "lastFlash"
                    )
                ) 1f else 0f
            )
        }
        choreographer.setPlaybackRate(1f)


        triggers // to initialize
        initVisibleObjects()
        if (ghostOverlay.isActive) {
            createGhostBody()
        }
        initialized = true
    }

    private fun scheduleFadeIn(
        maxValue: Float = 1f,
        length: Float = 0.5f,
        colorComponent: Float = 0f,
        post: (() -> Unit)? = null
    ) {
        tempColor.set(colorComponent, colorComponent, colorComponent, 0f)
        if (eventState.isEmpty()) {
            ui.setFadeEverythingColor(tempColor)
            ui.setFadeEverything(maxValue)
            timedActions += TimedAction(length, { ui.setFadeEverything(maxValue * it) }, {
                ui.setFadeEverything(0f)
                post?.invoke()
            })
        } else {
            ui.setFadeWorldColor(tempColor)
            ui.setFadeWorld(maxValue)
            timedActions += TimedAction(length, { ui.setFadeWorld(maxValue * it) }) {
                ui.setFadeWorld(0f)
                post?.invoke()
            }
        }
    }

    private fun scheduleFadeOut(
        maxValue: Float = 1f,
        length: Float = 0.5f,
        colorComponent: Float = 0f,
        post: (() -> Unit)? = null
    ) {
        tempColor.set(colorComponent, colorComponent, colorComponent, 0f)
        ui.setFadeWorldColor(tempColor)
        ui.setFadeWorld(0f)
        timedActions += TimedAction(length, { ui.setFadeWorld(maxValue * (1f - it)) }) {
            ui.setFadeWorld(maxValue)
            post?.invoke()
        }
    }

    private fun scheduleGameOver() {
        timedActions += TimedAction(0.5f, { ui.setFadeWorld(1f - it) }) {
            ui.setFadeWorld(1f)
            onGameOver()
        }
    }

    private val tempVec2 = Vec2()
    private fun updateWorld(dt: Duration, camera: Camera) {
        dream?.let {
            if (it.update(dt.seconds)) {
                eventExecutor.advance()
                return
            }
        }
        ghostFromPowerPlant?.let {
            if (it.update(dt.seconds)) {
                eventExecutor.advance()
                return
            }
        }
        transformation?.let {
            if (it.update(dt.seconds)) {
                eventExecutor.advance()
                return
            }
        }
        if (waitForDisappear) {
            if (!ghostOverlay.isActive) {
                waitForDisappear = false
                eventExecutor.advance()
            }
            return
        }

        val millis = dt.milliseconds
        time += dt.seconds
        var remainder = dt.seconds
        while (timedActions.isNotEmpty()) {
            val activeAction = timedActions.first()
            val actionRemainder = activeAction.update(remainder)
            if (actionRemainder > 0f) {
                break
            } else {
                timedActions.removeFirst()
                remainder = -actionRemainder
            }
        }

        ghostBody?.let { ghostBody ->
            if (ghostOverlay.isMoving) {
                ghostBody.updatePosition(
                    tempVec2.set(ghostOverlay.ghostPosition.x, ghostOverlay.ghostPosition.y)
                        .addLocal(
                            cameraMan.position.x - visibleWorldWidth / 2f,
                            cameraMan.position.y - visibleWorldHeight / 2f
                        )
                )
                if (ghostCooldown > 0f) {
                    ghostCooldown -= dt.seconds
                    if (ghostCooldown <= 0f) {
                        ghostCooldown = 0f
                    }
                }
            } else if (ghostCasting > 0f) {
                ghostCasting -= dt.seconds
                if (ghostCasting <= 0f) {
                    if (ghostBody.targetEnemies.isNotEmpty()) {
                        ghostBody.targetEnemies.forEach {
                            it.hit(ghostBody.position, 3)
                        }
                        ghostBody.targetEnemies.clear()
                    }
                    ghostStaying = GhostOverlay.postCastTime + ghostCasting
                    ghostCasting = 0f
                }
            } else if (ghostStaying > 0f) {
                ghostStaying -= dt.seconds
                if (ghostStaying <= 0f) {
                    ghostOverlay.isMoving = true
                    ghostCooldown = GhostOverlay.castCooldown + ghostStaying
                    ghostStaying = 0f
                }
            }
        }

        depthBasedDrawables.forEach {
            it.update(
                dt,
                millis,
                notAdjustedDt,
                choreographer.toBeat,
                choreographer.toMeasure,
                isFighting = enemies.isNotEmpty() || playerKnowledge.isEmpty(),
            )
        }
        if (addNeonGhostToList) {
            depthBasedDrawables.add(neonGhost!!)
            addNeonGhostToList = false
        }
        if (removeNeonGhostFromList) {
            depthBasedDrawables.remove(neonGhost!!)
            neonGhost = null
            removeNeonGhostFromList = false
        }
        if (deadEnemies.isNotEmpty()) {
            enemies.removeAll(deadEnemies)
            depthBasedDrawables.removeAll(deadEnemies)
            ui.stopShowingHealth(deadEnemies)
            deadEnemies.forEach { it.release() }
            deadEnemies.clear()
            if (enemies.size == 0) {
                probablyReturnPreviousMusicIfNotAskedForOther = true
                eventExecutor.advance()
                if (probablyReturnPreviousMusicIfNotAskedForOther) {
                    musicCommand("previous")
                }
            }
        }
        if (particlersToAdd.isNotEmpty()) {
            depthBasedDrawables.addAll(particlersToAdd)
            particlersToAdd.clear()
        }
        if (particlersToRemove.isNotEmpty()) {
            depthBasedDrawables.removeAll(particlersToRemove)
            particlersToRemove.clear()
        }
        //assets.objects.particleSimulator.update(dt)
        worldAccumulator += dt.seconds
        while (worldAccumulator >= worldStep) {
            world.step(worldStep, 6, 2)
            worldAccumulator -= worldStep
        }
        neonGhost?.let { ghost ->
            neonWorldAccumulator += notAdjustedDt.seconds
            while (neonWorldAccumulator >= worldStep) {
                neonWorld.step(worldStep, 6, 2)
                neonWorldAccumulator -= worldStep
            }
        }
        processTriggers()
        depthBasedDrawables.sortBy { it.depth }
        cameraMan.update(dt)

        if (ghostOverlay.isMoving && ghostCooldown <= 0f) {
            ghostBody?.let { ghostBody ->
                if (ghostBody.targetEnemies.isNotEmpty()) {
                    ghostOverlay.isMoving = false
                    ghostCasting = GhostOverlay.castTime
                }
            }
        }

        camera.position.set(cameraMan.position.x, cameraMan.position.y, 0f)

        if (closeTutorialOnNextUpdate && !inputController.pressed(GameInput.ATTACK) && player.punchCooldown <= 100f) {
            closeTutorialOnNextUpdate = false
            triggers.get("tutorial_trigger")?.let {
                eventExecutor.execute(it, playerKnowledge)
            }
        }
    }

    private var shapeRenderer: ShapeRenderer? = null
    private val tempVec2f = MutableVec2f()
    private fun renderWorld(dt: Duration, camera: Camera, batch: Batch) {
        dream?.let {
            it.render(batch)
            return
        }

        if (shapeRenderer == null) {
            shapeRenderer = ShapeRenderer(batch)
        }
        backgroundLayer?.render(
            batch,
            camera,
            x = 0f,
            y = 0f,
            scale = IPPU,
            displayObjects = false
        )
        floorLayer.render(batch, camera, x = 0f, y = 0f, scale = IPPU, displayObjects = false)
        if (ghostOverlay.isActive && enemies.isNotEmpty()) {
            tempVec2f.set(ghostOverlay.ghostPosition).add(
                cameraMan.position.x - visibleWorldWidth / 2f,
                cameraMan.position.y - visibleWorldHeight / 2f
            )
            if (ghostCasting > 0f /*|| ghostStaying > 0f*/) {
                val casted =
                    /*if (ghostStaying > 0f) 1f else */
                    (GhostOverlay.castTime - ghostCasting) / GhostOverlay.castTime
                shapeRenderer!!.filledEllipse(
                    x = tempVec2f.x,
                    y = tempVec2f.y,
                    rx = GhostOverlay.radiusX * casted,
                    ry = GhostOverlay.radiusY * casted,
                    innerColor = ghostFillColor,
                    outerColor = ghostFillColor,
                )
            }
            val casted = GhostOverlay.castCooldown - ghostCooldown
            if (casted > 0f && ghostStaying == 0f) {
                val startAngle = (0.0).radians
                val angle = casted / GhostOverlay.castCooldown * PI2
                shapeRenderer!!.ellipse(
                    tempVec2f.x,
                    tempVec2f.y,
                    rx = GhostOverlay.radiusX,
                    ry = GhostOverlay.radiusY,
                    color = ghostEdgeColor,
                    thickness = IPPU,
                    startAngle = startAngle,
                    radians = angle.toFloat(),
                )
            }
        }
        depthBasedDrawables.forEach { it.renderShadow(shapeRenderer!!) }
        wallLayer.render(batch, camera, x = 0f, y = 0f, scale = IPPU, displayObjects = false)
        decorationLayer.render(
            batch,
            camera,
            x = 0f,
            y = 0f,
            scale = IPPU,
            displayObjects = false
        )
        decorationLayer2?.render(
            batch,
            camera,
            x = 0f,
            y = 0f,
            scale = IPPU,
            displayObjects = false
        )
        depthBasedDrawables.forEach { it.render(batch) }
        if (transformation == null) {
            foregroundLayer.render(
                batch,
                camera,
                x = 0f,
                y = 0f,
                scale = IPPU,
                displayObjects = false
            )
        }
        transformation?.render(batch)
    }

    private fun updateUi(dt: Duration, camera: Camera) {
        ui.update(dt)
        camera.position.x = Game.virtualWidth / 2f
        camera.position.y = Game.virtualHeight / 2f
    }

    private fun renderUi(dt: Duration, camera: Camera, batch: Batch) {
        ui.render(
            choreographer.toMeasure,
            player.movingToBeat,
            player.movingOffBeat,
            enemies.isNotEmpty() || playerKnowledge.isEmpty()
        )
        ghostFromPowerPlant?.render(batch)
    }

    private var gameOverTimer = 2f
    fun updateAndRender(dt: Duration, notAdjustedDt: Duration) {
        this.notAdjustedDt = notAdjustedDt
        worldRender.render(dt)
        uiRenderer.render(dt)
        if (player.health <= 0 && dt.seconds <= 0.01f) {
            if (gameOverTimer > 0f) {
                gameOverTimer -= notAdjustedDt.seconds
                if (gameOverTimer <= 0f) {
                    musicCommand("stop")
                    //choreographer.play(assets.music.concurrentTracks["stop"]!!)
                    onGameOver()
                }
            }
        }
    }

    fun onAnimationEvent(event: String) {
        when (event) {
            "punch" -> {
                player.activatePunch()
                neonGhost?.activatePunch()
            }

            "footstep" -> {
                choreographer.sound(assets.sound.footstep.sound, player.x, player.y)
            }
        }
    }
}