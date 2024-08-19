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
    private val inputController: InputMapController<GameInput>
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

    val ui = UI(context)

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
            context.vfs
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
        when (trigger.name) {
            "fight1" -> {
                trigger.release()
                triggers.remove(trigger)
                cameraMan.lookAt(null)
                cameraMan.restricting = true
                enemies.add(createEnemy(assets.animation.punkAnimations, 1f))
                enemies.add(createEnemy(assets.animation.guardAnimations, 3f))
                depthBasedDrawables.addAll(enemies)
            }
        }
    }

    private fun createEnemy(characterAnimations: CharacterAnimations, maxSpeed: Float): Enemy {
        val offsetX =
            if (Random.nextBoolean()) -Game.visibleWorldWidth / 2f - 0.2f else Game.visibleWorldWidth / 2f + 0.2f
        val enemy = Enemy(
            Vec2(cameraMan.position.x + offsetX, player.y),
            player,
            world,
            assets,
            characterAnimations,
            inputController,
            assets.objects.particleSimulator,
            context.vfs,
            maxSpeed = maxSpeed
        )
        enemy.isAggressive = true
        return enemy
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

    private fun updateWorld(dt: Duration, camera: Camera) {
        val millis = dt.milliseconds
        time += dt.seconds
        toBeat = (time % secondsPerBeat) / secondsPerBeat
        toMeasure = (time % secondsPerMeasure) / secondsPerMeasure

        depthBasedDrawables.forEach { it.update(dt, millis, toBeat, toMeasure) }
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

    fun resize(width: Int, height: Int) {

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