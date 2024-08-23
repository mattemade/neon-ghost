package io.itch.mattemade.neonghost.scene

import com.littlekt.Context
import com.littlekt.graphics.Camera
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.graphics.g2d.tilemap.tiled.TiledMap
import com.littlekt.graphics.g2d.tilemap.tiled.TiledObjectLayer
import com.littlekt.graphics.g2d.tilemap.tiled.TiledTilesLayer
import com.littlekt.graphics.toFloatBits
import com.littlekt.input.InputMapController
import com.littlekt.math.MutableVec2f
import com.littlekt.util.datastructure.BiMap
import com.littlekt.util.milliseconds
import com.littlekt.util.seconds
import com.soywiz.kds.fastCastTo
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.Game.Companion.IPPU
import io.itch.mattemade.neonghost.Game.Companion.visibleWorldHeight
import io.itch.mattemade.neonghost.Game.Companion.visibleWorldWidth
import io.itch.mattemade.neonghost.character.DepthBasedRenderable
import io.itch.mattemade.neonghost.character.enemy.Enemy
import io.itch.mattemade.neonghost.character.rei.NeonGhost
import io.itch.mattemade.neonghost.character.rei.Player
import io.itch.mattemade.neonghost.event.EventExecutor
import io.itch.mattemade.neonghost.tempo.Choreographer
import io.itch.mattemade.neonghost.tempo.UI
import io.itch.mattemade.neonghost.world.CameraMan
import io.itch.mattemade.neonghost.world.Floor
import io.itch.mattemade.neonghost.world.GeneralContactListener
import io.itch.mattemade.neonghost.world.GhostBody
import io.itch.mattemade.neonghost.world.Trigger
import io.itch.mattemade.utils.math.belongsToEllipse
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import io.itch.mattemade.utils.render.PixelRender
import io.itch.mattemade.utils.render.createPixelFrameBuffer
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.World
import kotlin.random.Random
import kotlin.time.Duration

class InGame(
    private val context: Context,
    private val assets: Assets,
    private val level: TiledMap,
    private val inputController: InputMapController<GameInput>,
    private val choreographer: Choreographer,
    private val ghostOverlay: GhostOverlay,
    private val eventState: MutableMap<String, Int>,
    private val onGameOver: () -> Unit,
) : Releasing by Self() {

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
    private val floorLayer = tiledLayers.first { it.name == "floor" }
    private val wallLayer = tiledLayers.first { it.name == "wall" }
    private val decorationLayer = tiledLayers.first { it.name == "decoration" }
    private val foregroundLayer = tiledLayers.first { it.name == "foreground" }
    private var notAdjustedTime = 0f
    private var notAdjustedDt: Duration = Duration.ZERO

    private val ui by lazy {
        UI(
            context,
            assets,
            player,
            inputController,
            ::advanceDialog,
            ::activateInteraction,
            ::selectOption
        )
    }

    private val eventExecutor by lazy {
        EventExecutor(
            assets,
            player,
            ui,
            cameraMan,
            eventState,
            ::createEnemy,
            ::onTriggerEventCallback,
            ::onEventFinished
        )
    }
    private val isInDialogue: Boolean get() = eventExecutor.isInDialogue
    private fun advanceDialog() {
        eventExecutor.advance()
    }

    private fun activateInteraction(trigger: Trigger) {
        eventExecutor.execute(trigger)
    }

    private fun selectOption(key: String) {
        eventExecutor.selectOption(key)
    }


    private val levelHeight by lazy { level.height * level.tileHeight }
    private val floors = mutableListOf<Floor>()
    private val world by lazy {
        World(gravityX = 0f, gravityY = 0f).apply {
            val mapHeight = levelHeight
            level.layers.asSequence().filterIsInstance<TiledObjectLayer>().forEach {
                if (it.name == "floor") {
                    it.objects.forEach {
                        floors += Floor(this, it.bounds, mapHeight)
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
        val placement = level.layer("spawn").fastCastTo<TiledObjectLayer>().objects.first()
        val x = placement.bounds.x + placement.bounds.width / 2f
        val y = levelHeight - (placement.bounds.y - placement.bounds.height / 2f)
        Vec2(x * Game.IPPU, y * Game.IPPU)
    }
    private val player by lazy {
        Player(
            playerSpawnPosition,
            world,
            assets,
            inputController,
            assets.objects.particleSimulator,
            context.vfs,
            isMagicGirl = false,
            initialHealth = 10,
            canAct = { !isInDialogue },
            gameOver = ::gameOver,
            changePlaybackRate = ::changePlaybackRate,
            spawnNeonGhost = ::spawnNeonGhost,
            castAoe = ::castAoe,
            castProjectile = ::castProjectile,
        ).also { it.transform() }
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
        enemies.forEach {
            if (belongsToEllipse(
                    it.x,
                    it.y,
                    position.x,
                    position.y,
                    Player.spellRx,
                    Player.spellRy,
                    it.extraForEllipseCheck,
            )) {
                it.hit(position, power * 2 + 1, fromSpell = true)
            }
        }
        println("casting aoe at $position with $power")
    }

    private fun castProjectile(position: Vec2, facingLeft: Boolean) {
        castProjectile(position, Player.castsToStopTime, facingLeft)
    }

    private fun castProjectile(position: Vec2, power: Int, facingLeft: Boolean) {
        println("casting projectile at $position with $power, isLeft = $facingLeft")
    }

    private val depthBasedDrawables by lazy {
        mutableListOf<DepthBasedRenderable>(
            player,
            /*enemy1,
            enemy2*/
        )
    }
    private val enemies = mutableListOf<Enemy>()
    private val deadEnemies = mutableListOf<Enemy>()

    private val cameraMan by lazy {
        val tempVec2 = Vec2(playerSpawnPosition.x, Game.visibleWorldHeight / 2f)
        CameraMan(world, tempVec2).apply {
            lookAt { it.set(player.x, Game.visibleWorldHeight / 2f) }
        }
    }

    private val triggers: BiMap<String, Trigger> by lazy {
        val result = BiMap<String, Trigger>()
        level.layers.asSequence().filterIsInstance<TiledObjectLayer>()
            .first { it.name == "trigger" }.objects.forEach {
                val triggerState = eventState[it.name]
                if (triggerState == null || it.properties.containsKey(triggerState.toString())) {
                    result.put(
                        it.name, Trigger(
                            world,
                            it.bounds,
                            levelHeight,
                            it.name,
                            it.properties
                        )
                    )
                }
            }
        result
    }

    private val worldStep = 1f / 60f
    private var worldAccumulator = 0f
    private var neonWorldAccumulator = 0f

    private var enterTriggers = mutableSetOf<Trigger>()
    private var exitTriggers = mutableSetOf<Trigger>()

    private fun onEventFinished(trigger: Trigger) {
        trigger.deactivated() // to remove if it went out of states
    }

    private fun onTriggerEventCallback(event: String) {
        when (event) {
            "setMusic3b" -> choreographer.play(assets.music.background)
            "setMusic1c" -> choreographer.play(assets.music.background1c)
            "faster" -> choreographer.setPlaybackRate(choreographer.playbackRate.toFloat() + 0.25f)
            "slower" -> choreographer.setPlaybackRate(choreographer.playbackRate.toFloat() - 0.25f)
            "launchGhost" -> {
                ghostOverlay.activate()
                createGhostBody()
            }
            "transform" -> player.transform()
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
                    "trigger" -> eventExecutor.execute(triggger)
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
        val eventState = eventState[name] ?: return false
        val shouldBeRemoved = !properties.containsKey(eventState.toString())
        if (shouldBeRemoved) {
            triggers.removeValue(this)
            release()
        }
        return shouldBeRemoved
    }

    private var ghostBody: GhostBody? = null
    private fun createGhostBody() {
        if (ghostBody == null) {
            ghostBody = GhostBody(world, ghostOverlay)
        }
    }

    private fun createEnemy(
        enemySpec: EventExecutor.EnemySpec
    ): Enemy {
        val offsetX =
            if (Random.nextBoolean()) -Game.visibleWorldWidth / 2f - 0.5f else Game.visibleWorldWidth / 2f + 0.5f
        val offsetY =
            Game.visibleWorldHeight * 4f / 5f - Random.nextFloat() * Game.visibleWorldHeight / 3f
        val enemy = Enemy(
            Vec2(cameraMan.position.x + offsetX, offsetY),
            player,
            world,
            assets,
            enemySpec.characterAnimations,
            inputController,
            assets.objects.particleSimulator,
            context.vfs,
            difficulty = enemySpec.difficulty,
            initialHeath = enemySpec.health,
            canAct = { !isInDialogue },
            ::onEnemyDeath
        )
        enemies.add(enemy)
        depthBasedDrawables.add(enemy)
        ui.showHealth(enemy)
        enemy.isAggressive = true
        return enemy
    }

    private fun onEnemyDeath(enemy: Enemy) {
        deadEnemies += enemy
    }

    private fun gameOver() {
        onGameOver()
    }

    private var time = 0f
    private var ghostCasting = 0f
    private var ghostStaying = 0f
    private var ghostCooldown = 0f
    private val ghostEdgeColor = MutableColor(1f, 1f, 1f, 0.9f).toFloatBits()
    private val ghostFillColor = MutableColor(1f, 1f, 1f, 0.75f).toFloatBits()

    private var music = false

    init {
        choreographer.play(assets.music.background)
        triggers // to initialize
        if (ghostOverlay.isActive) {
            createGhostBody()
        }
    }

    private val tempVec2 = Vec2()
    private fun updateWorld(dt: Duration, camera: Camera) {
        val millis = dt.milliseconds
        time += dt.seconds

        ghostBody?.let { ghostBody ->
            if (ghostOverlay.isMoving) {
                ghostBody.updatePosition(
                    tempVec2.set(ghostOverlay.ghostPosition.x, ghostOverlay.ghostPosition.y).addLocal(
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
                choreographer.toMeasure
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
                eventExecutor.advance()
            }
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
    }

    private var shapeRenderer: ShapeRenderer? = null
    private val tempVec2f = MutableVec2f()
    private fun renderWorld(dt: Duration, camera: Camera, batch: Batch) {
        if (shapeRenderer == null) {
            shapeRenderer = ShapeRenderer(batch)
        }
        floorLayer.render(batch, camera, x = 0f, y = 0f, scale = IPPU, displayObjects = false)
        if (ghostOverlay.isActive) {
            tempVec2f.set(ghostOverlay.ghostPosition).add(
                cameraMan.position.x - visibleWorldWidth / 2f,
                cameraMan.position.y - visibleWorldHeight / 2f
            )
            if (ghostCasting > 0f /*|| ghostStaying > 0f*/) {
                val casted =
                    /*if (ghostStaying > 0f) 1f else */(GhostOverlay.castTime - ghostCasting) / GhostOverlay.castTime
                shapeRenderer!!.filledEllipse(
                    x = tempVec2f.x,
                    y = tempVec2f.y,
                    rx = GhostOverlay.radiusX * casted,
                    ry = GhostOverlay.radiusY * casted,
                    innerColor = ghostFillColor,
                    outerColor = ghostFillColor,
                )
            }
            if (ghostCooldown <= 0f && ghostStaying == 0f) {
                shapeRenderer!!.ellipse(
                    tempVec2f,
                    rx = GhostOverlay.radiusX,
                    ry = GhostOverlay.radiusY,
                    color = ghostEdgeColor,
                    thickness = IPPU
                )
            }
        }
        depthBasedDrawables.forEach { it.renderShadow(shapeRenderer!!) }
        wallLayer.render(batch, camera, x = 0f, y = 0f, scale = IPPU, displayObjects = false)
        decorationLayer.render(batch, camera, x = 0f, y = 0f, scale = IPPU, displayObjects = false)
        depthBasedDrawables.forEach { it.render(batch) }
        foregroundLayer.render(batch, camera, x = 0f, y = 0f, scale = IPPU, displayObjects = false)
    }

    private fun updateUi(dt: Duration, camera: Camera) {
        ui.update(dt)
        camera.position.x = Game.virtualWidth / 2f
        camera.position.y = Game.virtualHeight / 2f
    }

    private fun renderUi(dt: Duration, camera: Camera, batch: Batch) {
        ui.render(choreographer.toMeasure, player.movingToBeat, player.movingOffBeat)
    }

    fun updateAndRender(dt: Duration, notAdjustedDt: Duration) {
        this.notAdjustedDt = notAdjustedDt
        worldRender.render(dt)
        uiRenderer.render(dt)
    }

    fun onAnimationEvent(event: String) {
        when (event) {
            "punch" -> {
                player.activatePunch()
                neonGhost?.activatePunch()
            }
        }
    }
}