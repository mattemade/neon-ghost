package io.itch.mattemade.neonghost.scene

import com.littlekt.Context
import com.littlekt.graphics.Camera
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.tilemap.tiled.TiledMap
import com.littlekt.graphics.g2d.tilemap.tiled.TiledObjectLayer
import com.littlekt.input.InputMapController
import com.littlekt.input.InputMapProcessor
import com.littlekt.util.milliseconds
import com.littlekt.util.seconds
import com.soywiz.kds.fastCastTo
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.CharacterAnimations
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.Game.Companion.IPPU
import io.itch.mattemade.neonghost.character.DepthBasedRenderable
import io.itch.mattemade.neonghost.character.enemy.Enemy
import io.itch.mattemade.neonghost.character.rei.Player
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

    private val ui by lazy { UI(context, player, inputController) }
    private val isInDialogue: Boolean get() = ui.isInDialogue

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
            setContactListener(GeneralContactListener(::onTriggerEvent))
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
            lookAt { tempVec2.set(player.x, Game.visibleWorldHeight / 2f) }
            //setRestricting(true)
        }
    }

    private val triggers: MutableList<Trigger> by lazy {
        level.layers.asSequence().filterIsInstance<TiledObjectLayer>()
            .first { it.name == "trigger" }.objects.map {
                Trigger(
                    world,
                    it.bounds,
                    levelHeight,
                    it.name
                )
            }.toMutableList()
    }

    private val worldStep = 1f / 60f
    private var worldAccumulator = 0f

    private var triggeredEvents = mutableListOf<Trigger>()

    private fun onTriggerEvent(trigger: Trigger) {
        println("triggered ${trigger.name}")
        triggeredEvents.add(trigger)
    }

    private fun processTriggers() {
        triggeredEvents.forEach {
            processTrigger(it)
        }
        triggeredEvents.clear()
    }

    private fun processTrigger(trigger: Trigger) {
        trigger.release()
        triggers.remove(trigger)
        when (trigger.name) {
            "dialogue1" -> ui.launchScript(assets.script.test1)
            "fight1" -> spawnEnemies(1)
            "fight2" -> spawnEnemies(2)
            "fight3" -> spawnEnemies(3)
        }
    }

    private fun spawnEnemies(count: Int = 1) {
        cameraMan.lookAt(null)
        cameraMan.restricting = true
        when (count) {
            1 -> {
                createEnemy(assets.animation.punkAnimations, 1f, 5)
                createEnemy(assets.animation.punkAnimations, 1f, 5)
            }

            2 -> {
                createEnemy(assets.animation.guardAnimations, 3f, 8)
                createEnemy(assets.animation.guardAnimations, 3f, 8)
                createEnemy(assets.animation.guardAnimations, 3f, 8)
            }

            3 -> {
                createEnemy(assets.animation.punkAnimations, 1f, 5)
                createEnemy(assets.animation.punkAnimations, 1f, 5)
                createEnemy(assets.animation.guardAnimations, 3f, 8)
                createEnemy(assets.animation.guardAnimations, 3f, 8)
                createEnemy(assets.animation.officerAnimations, 5f, 20)
            }
        }
        createEnemy(assets.animation.punkAnimations, 1f, 5)
        /*createEnemy(assets.animation.guardAnimations, 3f, 8)
        createEnemy(assets.animation.punkAnimations, 1f, 5)
        createEnemy(assets.animation.officerAnimations, 5f, 20)*/
    }

    private fun createEnemy(
        characterAnimations: CharacterAnimations,
        difficulty: Float,
        health: Int
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
            characterAnimations,
            inputController,
            assets.objects.particleSimulator,
            context.vfs,
            difficulty = difficulty,
            initialHeath = health,
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
        inputController.addInputMapProcessor(object: InputMapProcessor<GameInput> {
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

    private fun makeCameraLookAtPlayer(delay: Float) {
        val started = time
        val shouldBeAtPlayerAt = time + delay
        val position = Vec2()
        val startPositionX = cameraMan.position.x
        cameraMan.lookAt {
            if (time >= shouldBeAtPlayerAt) {
                position.set(player.x, Game.visibleWorldHeight / 2f)
            } else {
                val passed = time - started
                position.set(
                    startPositionX + (player.x - startPositionX) * interpolate(passed / delay),
                    Game.visibleWorldHeight / 2f
                )
            }
            position
        }

        cameraMan.restricting = false
    }

    private fun interpolate(value: Float): Float = 3 * value * value - 2 * value * value * value

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
                makeCameraLookAtPlayer(2f)
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
        cameraMan.update()

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
        println("general animation event: $event")
        when (event) {
            "punch" -> player.activatePunch()
        }
    }
}