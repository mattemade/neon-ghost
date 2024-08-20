package com.game.template.scene

import com.game.template.Assets
import com.game.template.CharacterAnimations
import com.game.template.Game
import com.game.template.Game.Companion.IPPU
import com.game.template.Game.Companion.virtualHeight
import com.game.template.Game.Companion.virtualWidth
import com.game.template.character.DepthBasedRenderable
import com.game.template.character.enemy.Enemy
import com.game.template.character.rei.Player
import com.game.template.tempo.UI
import com.game.template.world.CameraMan
import com.game.template.world.Floor
import com.game.template.world.GeneralContactListener
import com.game.template.world.Trigger
import com.littlekt.Context
import com.littlekt.graphics.Camera
import com.littlekt.graphics.Color
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.tilemap.tiled.TiledObjectLayer
import com.littlekt.graphics.gl.BlendFactor
import com.littlekt.graphics.gl.State
import com.littlekt.input.InputMapController
import com.littlekt.util.milliseconds
import com.littlekt.util.seconds
import io.itch.mattemade.blackcat.input.GameInput
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
    private val inputController: InputMapController<GameInput>,
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
        ::renderWorld
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

    val ui by lazy { UI(context, player) }

    private val floors = mutableListOf<Floor>()
    private val world by lazy {
        World(gravityX = 0f, gravityY = 0f).apply {
            val level = assets.level.testRoom
            val mapHeight = level.height * level.tileHeight
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
    private val player by lazy {
        Player(
            Vec2(100f / Game.PPU, 100f / Game.PPU),
            world,
            assets,
            inputController,
            assets.objects.particleSimulator,
            context.vfs,
            initialHealth = 10,
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
        val tempVec2 = Vec2()
        CameraMan(world).apply {
            lookAt { tempVec2.set(player.x, Game.visibleWorldHeight / 2f) }
            //setRestricting(true)
        }
    }

    private val triggers: MutableList<Trigger> by lazy {
        assets.level.testRoom.layers.asSequence().filterIsInstance<TiledObjectLayer>()
            .first { it.name == "trigger" }.objects.map {
                Trigger(
                    world,
                    it.bounds,
                    assets.level.testRoom.height * assets.level.testRoom.tileHeight,
                    it.name
                )
            }.toMutableList()
    }

    val opaqueYellow = MutableColor(Color.YELLOW).also { it.a = 0.5f }
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

    private fun createEnemy(characterAnimations: CharacterAnimations, difficulty: Float, health: Int): Enemy {
        val offsetX =
            if (Random.nextBoolean()) -Game.visibleWorldWidth / 2f - 0.5f else Game.visibleWorldWidth / 2f + 0.5f
        val offsetY = Game.visibleWorldHeight * 4f / 5f - Random.nextFloat() * Game.visibleWorldHeight / 3f
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
        assets.music.background.stop()
        onGameOver()
    }

    private val bpm = 138.6882f//128.5714f
    private val secondsPerBeat = 60f / bpm
    private val doubleSecondsPerBeat = secondsPerBeat * 2f
    private val secondsPerMeasure = secondsPerBeat * 4
    private var time = -0.2f
    private var toBeat = (time % secondsPerBeat) / secondsPerBeat
    private var toMeasure = (time % secondsPerMeasure) / secondsPerMeasure

    init {
        if (!assets.music.background.playing) {
            context.audio.setListenerPosition(virtualWidth / 2f, virtualHeight / 2f, -200f)
            context.vfs.launch { assets.music.background.play(volume = 0.1f, loop = true) }
        }
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
                position.set(startPositionX + (player.x - startPositionX) * interpolate(passed / delay), Game.visibleWorldHeight / 2f)
            }
            position
        }

        cameraMan.restricting = false
    }

    private fun interpolate(value: Float): Float = 3 * value * value - 2 * value * value * value

    private fun updateWorld(dt: Duration, camera: Camera) {
        val millis = dt.milliseconds
        time += dt.seconds
        toBeat = (time % secondsPerBeat) / secondsPerBeat
        toMeasure = (time % secondsPerMeasure) / secondsPerMeasure

        depthBasedDrawables.forEach { it.update(dt, millis, toBeat, toMeasure) }
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
        cameraMan.update()

        camera.position.set(cameraMan.position.x, cameraMan.position.y, 0f)
    }

    private fun renderWorld(dt: Duration, camera: Camera, batch: Batch) {
        assets.level.testRoom.render(batch, camera, scale = IPPU)

        depthBasedDrawables.sortBy { it.depth }
        depthBasedDrawables.forEach { it.render(batch) }

        if (time % doubleSecondsPerBeat < secondsPerBeat) {
            opaqueYellow.set(Color.RED)
        } else {
            opaqueYellow.set(Color.GREEN)
        }
        opaqueYellow.a = 1f - (time % secondsPerBeat) / secondsPerBeat

        context.gl.enable(State.BLEND)
        batch.setBlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA)
        batch.setToPreviousBlendFunction()
        context.gl.disable(State.BLEND)
    }

    private fun updateUi(dt: Duration, camera: Camera) {
        camera.position.x = Game.virtualWidth / 2f
        camera.position.y = Game.virtualHeight / 2f
    }

    private fun renderUi(dt: Duration, camera: Camera, batch: Batch) {
        ui.render(toMeasure, player.movingToBeat, player.movingOffBeat)
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