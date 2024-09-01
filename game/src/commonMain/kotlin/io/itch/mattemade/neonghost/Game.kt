package io.itch.mattemade.neonghost

import com.littlekt.Context
import com.littlekt.ContextListener
import com.littlekt.file.createFloatBuffer
import com.littlekt.graph.node.resource.HAlign
import com.littlekt.graphics.Camera
import com.littlekt.graphics.Color
import com.littlekt.graphics.Fonts
import com.littlekt.graphics.GL
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.Textures
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.gl.BlendFactor
import com.littlekt.graphics.gl.ClearBufferMask
import com.littlekt.graphics.gl.GlVertexArray
import com.littlekt.graphics.gl.State
import com.littlekt.graphics.shader.ShaderProgram
import com.littlekt.graphics.toFloatBits
import com.littlekt.input.InputMapProcessor
import com.littlekt.input.InputProcessor
import com.littlekt.input.Pointer
import com.littlekt.math.MutableVec2f
import com.littlekt.util.milliseconds
import com.littlekt.util.seconds
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.blackcat.input.bindInputs
import io.itch.mattemade.neonghost.character.rei.Player
import io.itch.mattemade.neonghost.scene.GhostOverlay
import io.itch.mattemade.neonghost.scene.InGame
import io.itch.mattemade.neonghost.shader.CabinetFragmentShader
import io.itch.mattemade.neonghost.shader.CabinetVertexShader
import io.itch.mattemade.neonghost.shader.ParticleFragmentShader
import io.itch.mattemade.neonghost.shader.ParticleVertexShader
import io.itch.mattemade.neonghost.shader.Particler
import io.itch.mattemade.neonghost.shader.TestFragmentShader
import io.itch.mattemade.neonghost.shader.TestVertexShader
import io.itch.mattemade.neonghost.shader.createCabinetShader
import io.itch.mattemade.neonghost.shader.createParticleShader
import io.itch.mattemade.neonghost.shader.createTestShader
import io.itch.mattemade.neonghost.shader.representation.BoundableBuffer
import io.itch.mattemade.neonghost.tempo.Choreographer
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import io.itch.mattemade.utils.render.DirectRender
import org.jbox2d.internal.System_nanoTime
import kotlin.random.Random
import kotlin.time.Duration

class Game(
    context: Context,
    private val onLowPerformance: () -> Unit,
    savedStateOverride: SavedState? = null
) : ContextListener(context),
    Releasing by Self() {

    private val choreographer = Choreographer(context)
    var focused = false
        set(value) {
            field = value
            if (!value && assets.isLoaded && choreographer.isActive) {
                context.audio.suspend()
            } else if (value) {
                context.audio.resume()
            }
        }
    val assets = Assets(context, ::onAnimationEvent).releasing()
    val inputController = context.bindInputs()
    var inGame: InGame? = null
    val ghostOverlay by lazy { GhostOverlay(context, assets, choreographer, inputController, particleShader) }
    val directRender = DirectRender(context, virtualWidth, virtualHeight, ::update, ::render)
    val cabinetRender =
        DirectRender(context, virtualWidth, virtualHeight, ::updateCabinet, ::renderCabinet)
    lateinit var cabinetShader: ShaderProgram<CabinetVertexShader, CabinetFragmentShader>
    lateinit var particleShader: ShaderProgram<ParticleVertexShader, ParticleFragmentShader>
    lateinit var testShader: ShaderProgram<TestVertexShader, TestFragmentShader>
    var useCabinet = false
    var offsetX = 0f
    var offsetY = 0f
    var cabinetOffsetX = 0f
    var cabinetOffsetY = 0f
    var currentScreenWidth: Float = 0f
    var currentScreenHeight: Float = 0f
    var cabinetWidth: Float = 0f
    var cabinetHeight: Float = 0f
    var scale = 1
    var floatScale = 1f
    private var audioReady: Boolean = false
    private var assetsReady: Boolean = false
    private var fpsCheckTimeout = 5000f
    private var framesRenderedInPeriod = 0
    private var absoluteTime = Random.nextFloat() * 100f

    private val eventState = mutableMapOf<String, Int>()
    private val playerKnowledge = mutableSetOf<String>()
    private val interactionOverride = mutableMapOf<String, String>()

    private var savedState: SavedState? = savedStateOverride
    private var previousRoomName: String? = null
    private fun openDoor(door: String, toRoom: String, playerHealth: Int, isMagic: Boolean) {
        previousRoomName?.let { previousRoomName ->
            saveGame(door, previousRoomName, playerHealth, isMagic)
        }
        previousRoomName = toRoom
        assets.level.levels[toRoom]?.let { level ->
            startGameFromLevel(level, door, playerHealth, isMagic)
        } ?: error("no such level found $toRoom")
    }

    private fun startGameFromLevel(
        level: LevelSpec,
        wentThrough: String?,
        playerHealth: Int,
        isMagic: Boolean
    ) {
        inGame = InGame(
            context,
            assets,
            particleShader,
            level,
            inputController,
            choreographer,
            ghostOverlay,
            eventState,
            playerKnowledge,
            interactionOverride,
            onGameOver = ::loadGame,
            goThroughDoor = ::openDoor,
            wentThroughDoor = wentThrough,
            saveState = {
                saveGame(
                    wentThrough!!,
                    previousRoomName!!,
                    savedState!!.playerHealth,
                    savedState!!.isMagic
                )
            },
            loadState = {
                //TODO
            },
            playerHealth = playerHealth,
            isMagic = isMagic
        )
    }

    private fun saveGame(door: String, room: String, playerHealth: Int, isMagic: Boolean) {
        savedState = SavedState(
            door = door,
            room = room,
            eventState = mutableMapOf<String, Int>().apply { putAll(eventState) },
            playerKnowledge = mutableSetOf<String>().apply { addAll(playerKnowledge) },
            interactionOverride = mutableMapOf<String, String>().apply { putAll(interactionOverride) },
            ghostActive = ghostOverlay.isActive,
            playerHealth = playerHealth,
            isMagic = isMagic,
            activeMusic = choreographer.currentlyPlayingTrack?.name
        )
    }

    private fun loadGame() {
        savedState?.let {
            eventState.clear()
            eventState.putAll(it.eventState)
            playerKnowledge.clear()
            playerKnowledge.addAll(it.playerKnowledge)
            interactionOverride.clear()
            interactionOverride.putAll(it.interactionOverride)
            ghostOverlay.isActive = it.ghostActive
            ghostOverlay.isMoving = it.ghostActive
            it.activeMusic?.let { activeMusic ->
                choreographer.play(assets.music.concurrentTracks[activeMusic]!!)
            }
            previousRoomName = it.room
            openDoor(it.door, it.room, it.playerHealth, it.isMagic)
        } ?: resetGame()
    }

    private fun resetGame() {
        eventState.clear()
        playerKnowledge.clear()
        interactionOverride.clear()
        ghostOverlay.isActive = false
        ghostOverlay.isMoving = false
        previousRoomName = "boxing_club"
        openDoor("player", "boxing_club", Player.maxPlayerHealth, false)

        /*        choreographer.play(assets.music.concurrentTracks["magical girl 3d"]!!)
                eventState["officer_catch"] = 1
                previousRoomName = "interrogation_room"
                openDoor("officer_catch", "interrogation_room", 10, false)*/
    }

    private fun onAnimationEvent(event: String) {
        if (!focused) {
            return
        }
        inGame?.onAnimationEvent(event)
    }

    override suspend fun Context.start() {
        cabinetShader = createCabinetShader(
            vfs["shader/cabinet.vert.glsl"].readString(),
            vfs["shader/cabinet.frag.glsl"].readString()
        ).also { it.prepare(this) }

        particleShader = createParticleShader(
            vfs["shader/particles.vert.glsl"].readString(),
            vfs["shader/particles.frag.glsl"].readString()
        ).also { it.prepare(this) }

        testShader = createTestShader(
            vfs["shader/test.vert.glsl"].readString(),
            vfs["shader/test.frag.glsl"].readString()
        ).also { it.prepare(this) }

        input.addInputProcessor(object : InputProcessor {
            override fun touchUp(screenX: Float, screenY: Float, pointer: Pointer): Boolean {
                if (focused) {

                } else {
                    focused = true
                }
                return true
            }
        })
        inputController.addInputMapProcessor(object : InputMapProcessor<GameInput> {
            override fun onActionDown(inputType: GameInput): Boolean {
                if (focused) {

                } else {
                    focused = true
                }
                return false
            }
        })

        onResize { width, height ->
            val widthScale = width / virtualWidth
            val heightScale = height / virtualHeight
            scale = minOf(widthScale, heightScale)
            val scaledWidth = virtualWidth * scale
            val scaledHeight = virtualHeight * scale

            directRender.resize(width, height)
            cabinetRender.resize(width, height)
            offsetX = -scaledWidth * 0.5f
            offsetY = -scaledHeight * 0.5f
            focused = false


            currentScreenWidth = width.toFloat()
            currentScreenHeight = height.toFloat()
            floatScale =
                minOf(currentScreenWidth / virtualWidth, currentScreenHeight / virtualHeight)
            cabinetWidth = virtualWidth * floatScale
            cabinetHeight = virtualHeight * floatScale
            cabinetOffsetX = (width - cabinetWidth) / 2f
            cabinetOffsetY = (height - cabinetHeight) / 2f

            //cabinetShader.fragmentShader.uResolution.apply(cabinetShader, Vec2f(currentScreenWidth, currentScreenHeight))
        }

        onRender { dt ->
            gl.clear(ClearBufferMask.COLOR_BUFFER_BIT)
            gl.clearColor(Color.CLEAR)
            absoluteTime += dt.seconds
            if (!audioReady) {
                audioReady = audio.isReady()
            }
            if (!assetsReady) {
                assetsReady = audioReady && assets.isLoaded
            }
            if (focused && assetsReady) {
                if (inGame == null) {
                    if (savedState != null) {
                        loadGame()
                    } else {
                        resetGame()
                    }
                }
                choreographer.update(dt)
                inGame?.updateAndRender(choreographer.bpmBasedDt, dt)
                ghostOverlay.updateAndRender(choreographer.bpmBasedDt)
            }
            if (useCabinet) {
                cabinetRender.render(dt)
            } else {
                directRender.render(dt)

            }



            if (focused && assetsReady) {
                framesRenderedInPeriod++
                fpsCheckTimeout -= dt.milliseconds
                if (fpsCheckTimeout < 0f) {
                    if (framesRenderedInPeriod < 190) { // average is less than 38 fps
                        onLowPerformance()
                    }
                    fpsCheckTimeout = 5000f
                    framesRenderedInPeriod = 0
                }
            }

            val delay = 1000000000f / 1500000f
            val currentTime = System_nanoTime()
            while (System_nanoTime() - currentTime < delay) {

            }
        }

        onDispose(::release)
    }


    private fun update(duration: Duration, camera: Camera) {
        camera.position.x = 0f
        camera.position.y = 0f
    }

    private fun render(duration: Duration, batch: Batch) {
        if (!focused) {
            Fonts.default.draw(
                batch,
                "CLICK TO FOCUS",
                1.5f * PPU,
                0.5f * PPU,
                align = HAlign.CENTER,
                scale = scale.toFloat()
            )
        } else if (!assetsReady) {
            Fonts.default.draw(
                batch,
                "LOADING",
                1.5f * PPU,
                0.5f * PPU,
                align = HAlign.CENTER,
                scale = scale.toFloat()
            )
        } else {
            context.gl.enable(State.BLEND)
            batch.setBlendFunction(BlendFactor.ONE, BlendFactor.ONE)

            inGame?.texture?.let {
                batch.useDefaultShader()
                batch.draw(
                    it,
                    x = offsetX,
                    y = offsetY,
                    originX = 0f,
                    originY = 0f,
                    width = virtualWidth.toFloat() * scale,
                    height = virtualHeight.toFloat() * scale,
                    flipY = true,
                )
            }

            batch.setBlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.DST_ALPHA)
            batch.shader = assets.shader.test
            batch.draw(
                ghostOverlay.texture,
                x = offsetX,
                y = offsetY,
                originX = 0f,
                originY = 0f,
                width = virtualWidth.toFloat() * scale,
                height = virtualHeight.toFloat() * scale,
                colorBits = slightlyTransparentWhite.toFloatBits(),
                flipY = true
            )

            context.gl.disable(State.BLEND)
        }
    }

    private fun updateCabinet(duration: Duration, camera: Camera) {
        camera.position.x = currentScreenWidth / 2f
        camera.position.y = currentScreenHeight / 2f
    }

    private val temp = MutableVec2f(0f, 0f)
    private fun renderCabinet(duration: Duration, batch: Batch) {
        if (assetsReady) {
            inGame?.texture?.let { gameTexture ->
                batch.shader = cabinetShader
                ghostOverlay.texture.bind(1)
                cabinetShader.fragmentShader.uOverlayTexture.apply(cabinetShader, 1)
                cabinetShader.fragmentShader.uTime.apply(cabinetShader, absoluteTime)
                cabinetShader.fragmentShader.uScale.apply(cabinetShader, floatScale)
                cabinetShader.fragmentShader.uResolution.apply(cabinetShader, temp.set(cabinetWidth, cabinetHeight))
                batch.draw(
                    gameTexture,
                    x = cabinetOffsetX,
                    y = cabinetOffsetY,
                    width = cabinetWidth,
                    height = cabinetHeight,
                    flipY = true
                )
            }
        }
    }

    private val slightlyTransparentWhite = Color.WHITE.withAlpha(0.5f)

    class SavedState(
        val door: String,
        val room: String,
        val eventState: MutableMap<String, Int>,
        val playerKnowledge: MutableSet<String>,
        val interactionOverride: MutableMap<String, String>,
        val ghostActive: Boolean,
        val playerHealth: Int,
        val isMagic: Boolean,
        val activeMusic: String?,
    )

    companion object {
        const val PPU = 80f
        const val IPPU = 1 / 80f

        const val virtualWidth = 320//256
        const val virtualHeight = 240//224//240
        const val visibleWorldWidth = (virtualWidth / PPU).toInt()
        const val visibleWorldHeight = (virtualHeight / PPU).toInt()

        val shadowColor = MutableColor(0f, 0f, 0f, 0.25f).toFloatBits()
    }
}

val Float.screenSpacePixelPerfect: Float
    get() = this.toInt().toFloat()

val Float.pixelPerfectPosition: Float
    get() = (this * Game.PPU).toInt().toFloat() * Game.IPPU