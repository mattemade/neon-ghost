package io.itch.mattemade.neonghost.scene

import com.littlekt.Context
import com.littlekt.graphics.Camera
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.tilemap.tiled.TiledMap
import com.littlekt.graphics.g2d.tilemap.tiled.TiledObjectLayer
import com.littlekt.input.InputMapController
import com.littlekt.input.InputMapProcessor
import com.littlekt.util.datastructure.BiMap
import com.littlekt.util.milliseconds
import com.littlekt.util.seconds
import com.soywiz.kds.fastCastTo
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.Game.Companion.IPPU
import io.itch.mattemade.neonghost.character.DepthBasedRenderable
import io.itch.mattemade.neonghost.character.enemy.Enemy
import io.itch.mattemade.neonghost.character.rei.Player
import io.itch.mattemade.neonghost.event.EventExecutor
import io.itch.mattemade.neonghost.tempo.Choreographer
import io.itch.mattemade.neonghost.tempo.UI
import io.itch.mattemade.neonghost.world.CameraMan
import io.itch.mattemade.neonghost.world.Floor
import io.itch.mattemade.neonghost.world.GeneralContactListener
import io.itch.mattemade.neonghost.world.Trigger
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
    eventState: MutableMap<String, Int>,
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

    private val ui by lazy { UI(context, player, inputController, ::advanceDialog) }

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
            println("destorying body $it")
            destroyBody(it as Body)
        }
    }
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
            initialHealth = 10,
            canAct = { !isInDialogue },
            ::gameOver
        )
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
        result
    }

    private val worldStep = 1f / 60f
    private var worldAccumulator = 0f

    private var enterTriggers = mutableSetOf<Trigger>()
    private var exitTriggers = mutableSetOf<Trigger>()

    private fun onEventFinished() {

    }

    private fun onTriggerEventCallback(event: String) {
        println("event triggered ${event}")
    }

    private fun enterTrigger(trigger: Trigger) {
        println("triggered ${trigger.name}")
        enterTriggers.add(trigger)
    }
    private fun exitTrigger(trigger: Trigger) {
        println("exit from trigger ${trigger.name}")
        if (enterTriggers.contains(trigger)) {
            enterTriggers.remove(trigger)
        } else {
            exitTriggers.add(trigger)
        }
    }

    private fun processTriggers() {
        if (eventExecutor.isInDialogue || eventExecutor.isFighting) {
            return
        }
        enterTriggers.forEach {
            when (it.properties["type"]?.string) {
                "trigger" -> eventExecutor.execute(it)
                "interaction" -> {
                    // TODO: add interaction UI
                    println("adds interaction with ${it.name}")
                }
            }
        }
        enterTriggers.clear()
        exitTriggers.forEach {
            when (it.properties["type"]?.string) {
                "interaction" -> {
                    // TODO: remove interaction UI
                    println("removes interaction with ${it.name}")
                }
            }
        }
        exitTriggers.clear()
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

    private var music = false

    init {
        choreographer.play(assets.music.background)
        inputController.addInputMapProcessor(object : InputMapProcessor<GameInput> {
            override fun onActionDown(inputType: GameInput): Boolean {
                if (inputType == GameInput.START) {
                    choreographer.play(if (music) assets.music.background else assets.music.background1c)
                    music = !music
                }

                return false
            }
        })
        triggers // to initialize
    }

    private fun updateWorld(dt: Duration, camera: Camera) {
        val millis = dt.milliseconds
        time += dt.seconds

        depthBasedDrawables.forEach {
            it.update(
                dt,
                millis,
                choreographer.toBeat,
                choreographer.toMeasure
            )
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
        processTriggers()
        depthBasedDrawables.sortBy { it.depth }
        cameraMan.update(dt)

        camera.position.set(cameraMan.position.x, cameraMan.position.y, 0f)
    }

    private fun renderWorld(dt: Duration, camera: Camera, batch: Batch) {
        level.render(batch, camera, scale = IPPU)
        depthBasedDrawables.forEach { it.render(batch) }
    }

    private fun updateUi(dt: Duration, camera: Camera) {
        ui.update(dt)
        camera.position.x = Game.virtualWidth / 2f
        camera.position.y = Game.virtualHeight / 2f
    }

    private fun renderUi(dt: Duration, camera: Camera, batch: Batch) {
        ui.render(choreographer.toMeasure, player.movingToBeat, player.movingOffBeat)
    }

    fun updateAndRender(dt: Duration) {
        worldRender.render(dt)
        uiRenderer.render(dt)
    }

    fun onAnimationEvent(event: String) {
        when (event) {
            "punch" -> player.activatePunch()
        }
    }
}