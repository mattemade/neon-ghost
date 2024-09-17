package io.itch.mattemade.neonghost

import com.littlekt.Context
import com.littlekt.ContextListener
import com.littlekt.file.vfs.readTexture
import com.littlekt.graphics.Camera
import com.littlekt.graphics.Color
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.Texture
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.gl.BlendFactor
import com.littlekt.graphics.gl.ClearBufferMask
import com.littlekt.graphics.gl.State
import com.littlekt.graphics.shader.ShaderProgram
import com.littlekt.graphics.toFloatBits
import com.littlekt.input.InputMapProcessor
import com.littlekt.input.InputProcessor
import com.littlekt.input.Key
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
import io.itch.mattemade.neonghost.shader.createCabinetShader
import io.itch.mattemade.neonghost.shader.createParticleShader
import io.itch.mattemade.neonghost.tempo.Choreographer
import io.itch.mattemade.neonghost.touch.CombinedInput
import io.itch.mattemade.neonghost.touch.VirtualController
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import io.itch.mattemade.utils.render.DirectRender
import org.jbox2d.internal.System_nanoTime
import kotlin.random.Random
import kotlin.time.Duration

class Game(
    context: Context,
    private val onLowPerformance: (Boolean) -> Float,
    private val drawCabinet: Boolean = false,
    savedStateOverride: SavedState? = null,
    initialZoom: Float,
) : ContextListener(context),
    Releasing by Self() {

    private val choreographer = Choreographer(context)
    var focused = false
        set(value) {
            field = value
            if (!value) {
                virtualController.isVisible = false
            }
            if (!value && assets.isLoaded && choreographer.isActive) {
                context.audio.suspend()
            } else if (value) {
                context.audio.resume()
            }
        }
    val assets = Assets(context, ::onAnimationEvent).releasing()
    val extraAssets = ExtraAssets(context).releasing()
    val inputController = context.bindInputs()
    val virtualController = VirtualController(context, assets, initialZoom)
    val combinedInput = CombinedInput(inputController, virtualController)
    var inGame: InGame? = null
    val ghostOverlay by lazy {
        GhostOverlay(
            context,
            assets,
            choreographer,
            inputController,
            particleShader
        )
    }
    val directRender = DirectRender(context, virtualWidth, virtualHeight, ::update, ::render)
    val cabinetRender =
        DirectRender(context, virtualWidth, virtualHeight, ::updateCabinet, ::renderCabinet)
    lateinit var cabinetShader: ShaderProgram<CabinetVertexShader, CabinetFragmentShader>
    lateinit var particleShader: ShaderProgram<ParticleVertexShader, ParticleFragmentShader>
    lateinit var clickToFocus: Texture
    lateinit var loading: Texture

    //lateinit var testShader: ShaderProgram<TestVertexShader, TestFragmentShader>
    var useCabinet = drawCabinet
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
    private var extraAssetsReady: Boolean = false
    private var fpsCheckTimeout = 5000f
    private var framesRenderedInPeriod = 0
    private var absoluteTime = Random.nextFloat() * 100f

    private val eventState = mutableMapOf<String, Int>().apply {
        put("officer_boss", 1)
    }
    private val playerKnowledge = mutableSetOf<String>()
    private val interactionOverride = mutableMapOf<String, String>()

    private var savedState: SavedState? = savedStateOverride
    private var previousRoomName: String? = null
    private var previousDoorName: String? = null
    private var trackToPlayOnNextUpdate: StreamBpm? = null
    private var delayedOpenDoorArgs: OpenDoorArgs? = null
    private fun openDoor(
        door: String,
        toRoom: String,
        playerHealth: Int,
        isMagic: Boolean,
        deaths: Int,
        isLoaded: Boolean,
    ) {
        if (toRoom == "boxing_club" || extraAssetsReady) {
            delayedOpenDoorArgs = null
            previousRoomName?.let { previousRoomName ->
                saveGame(door, previousRoomName, playerHealth, isMagic, deaths)
            }
            previousRoomName = toRoom
            previousDoorName = door
            assets.level.levels[toRoom]?.let { level ->
                startGameFromLevel(level, door, playerHealth, isMagic, deaths, isLoaded)
            } ?: error("no such level found $toRoom")
        } else {
            delayedOpenDoorArgs = OpenDoorArgs(door, toRoom, playerHealth, isMagic, deaths)
        }
    }

    private fun startGameFromLevel(
        level: LevelSpec,
        wentThrough: String?,
        playerHealth: Int,
        isMagic: Boolean,
        deaths: Int,
        isLoaded: Boolean,
    ) {
        inGame = InGame(
            context,
            assets,
            extraAssets,
            particleShader,
            level,
            combinedInput,
            choreographer,
            ghostOverlay,
            eventState,
            playerKnowledge,
            interactionOverride,
            deaths = deaths,
            onGameOver = ::loadGame,
            goThroughDoor = ::openDoor,
            wentThroughDoor = wentThrough,
            saveState = {
                saveGame(
                    it ?: wentThrough!!,
                    previousRoomName!!,
                    savedState!!.playerHealth,
                    savedState!!.isMagic,
                    savedState!!.deaths
                )
            },
            restartGame = {
                resetGame()
            },
            playerHealth = playerHealth,
            isMagic = isMagic,
            isLoaded = isLoaded,
            onTransformed = { virtualController.isMagic = true }
        )
    }

    private fun saveGame(
        door: String,
        room: String,
        playerHealth: Int,
        isMagic: Boolean,
        deaths: Int = 0
    ) {
        savedState = SavedState(
            door = door,
            room = room,
            eventState = mutableMapOf<String, Int>().apply { putAll(eventState) },
            playerKnowledge = mutableSetOf<String>().apply { addAll(playerKnowledge) },
            interactionOverride = mutableMapOf<String, String>().apply { putAll(interactionOverride) },
            ghostActive = ghostOverlay.isActive,
            playerHealth = if (isMagic) Player.maxPlayerHealth * 2 else Player.maxPlayerHealth,//playerHealth,
            isMagic = isMagic,
            activeMusic = choreographer.currentlyPlayingTrack?.name,
            deaths = deaths,
        )
    }

    private fun loadGame() {
        val movingIndoor = playerKnowledge.contains("moveIndoor")
        savedState?.let {
            choreographer.reset()
            ghostOverlay.reset()
            eventState.clear()
            eventState.putAll(it.eventState)
            playerKnowledge.clear()
            playerKnowledge.addAll(it.playerKnowledge)
            interactionOverride.clear()
            interactionOverride.putAll(it.interactionOverride)
            ghostOverlay.isActive = it.ghostActive
            ghostOverlay.isMoving = it.ghostActive
            it.activeMusic?.let { activeMusic ->
                val track = assets.music.concurrentTracks[activeMusic]
                    ?: if (extraAssets.isLoaded) extraAssets.music.concurrentTracks[activeMusic] else null
                track?.let {
                    trackToPlayOnNextUpdate = it
                }
            }
            previousRoomName = it.room

            var magic = it.isMagic
            var health = Player.maxPlayerHealth
            if (playerKnowledge.contains("ending")) {
                magic = true
                virtualController.isMagic = true
                choreographer.play(assets.music.concurrentTracks["stop"]!!)
                choreographer.fullStop()
                ghostOverlay.isActive = false
                ghostOverlay.isMoving = false
            } else if (playerKnowledge.contains("magic")) {
                magic = true
                health = Player.maxPlayerHealth * 2
                virtualController.isMagic = true
                choreographer.holdMagicMusic()
                trackToPlayOnNextUpdate = extraAssets.music.concurrentTracks["magical girl optimistic"]!!
            }

            if (movingIndoor) {
                choreographer.startNextTrackWithoutFading = true
                trackToPlayOnNextUpdate = assets.music.concurrentTracks["stop"]!!
                playerKnowledge.remove("moveIndoor")
                eventState["officer_catch"] = 1
                previousRoomName = "interrogation_room"
                openDoor("officer_catch", previousRoomName!!, health, it.isMagic, it.deaths, false)
            } else {
                openDoor(it.door, it.room, health, magic, it.deaths + 1, true)
            }


        } ?: resetGame()
    }

    private fun resetGame() {
        virtualController.isMagic = false
        choreographer.reset()
        ghostOverlay.reset()
        eventState.clear()
        eventState.put("officer_boss", 1)
        playerKnowledge.clear()
        interactionOverride.clear()
        previousRoomName = "boxing_club"
        openDoor("player", "boxing_club", Player.maxPlayerHealth, false, 0, false)
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

        /*testShader = createTestShader(
            vfs["shader/test.vert.glsl"].readString(),
            vfs["shader/test.frag.glsl"].readString()
        ).also { it.prepare(this) }*/

        clickToFocus = resourcesVfs["texture/misc/click_to_focus.png"].readTexture()
        loading = resourcesVfs["texture/misc/loading.png"].readTexture()

        input.addInputProcessor(object : InputProcessor {
            override fun keyDown(key: Key): Boolean {
                if (key == Key.BACKSPACE) {
                    var arguments = "spawn=${previousDoorName}&room=${previousRoomName}"
                    playerKnowledge.forEach {
                        arguments += "&remember=$it"
                    }
                    eventState.forEach { (key, value) ->
                        arguments += "&$key=$value"
                    }
                    println(arguments)
                } else if (key == Key.DELETE) {
                    loadGame()
                }
                return false
            }

            override fun touchUp(screenX: Float, screenY: Float, pointer: Pointer): Boolean {
                if (focused) {
                    virtualController.isVisible = true
                } else {
                    focused = true
                }
                return false
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
            virtualController.resize(width, height)

            fpsCheckTimeout = 5000f
            framesRenderedInPeriod = 0
            // resizing to a higher resolution than was before
            if (width > directRender.postViewport.virtualWidth || height > directRender.postViewport.virtualHeight) {
                //useCabinet = true
                virtualController.zoom = onLowPerformance(true)// just to reset the zoom factor - it will auto-adjust in 5 seconds after
            }

            val widthScale = width / virtualWidth
            val heightScale = height / virtualHeight
            scale = minOf(widthScale, heightScale)
            val scaledWidth = virtualWidth * scale
            val scaledHeight = virtualHeight * scale

            if (scale < 3) {
                useCabinet = false
            }

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
            } else if (!extraAssetsReady) {
                extraAssetsReady = extraAssets.isLoaded
            }
            delayedOpenDoorArgs?.let {
                if (extraAssetsReady) {
                    openDoor(it.door, it.toRoom, it.playerHealth, it.isMagic, it.deaths, false)
                }
            }
            val trackToPlay = trackToPlayOnNextUpdate
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

                trackToPlay?.let {
                    choreographer.play(it)
                    trackToPlayOnNextUpdate = null
                }
            }

            if (focused && assetsReady) {
                virtualController.update()
            }
            if (useCabinet) {
                cabinetRender.render(dt)
            } else {
                directRender.render(dt)
            }
            if (focused && assetsReady) {
                virtualController.render(dt)
            }

            if (focused && assetsReady && extraAssetsReady) {
                framesRenderedInPeriod++
                fpsCheckTimeout -= dt.milliseconds
                if (fpsCheckTimeout < 0f) {
                    if (framesRenderedInPeriod < 0) { // average is less than 38 fps
                        if (useCabinet) {
                            val canZoomOutEvenMore =
                                directRender.postViewport.virtualWidth > virtualWidth * 5f &&
                                        directRender.postViewport.virtualHeight > virtualHeight * 5f
                            if (canZoomOutEvenMore) {
                                virtualController.zoom = onLowPerformance(false)
                            } else {
                                useCabinet = false
                                virtualController.zoom = onLowPerformance(true)
                            }
                        } else {
                            val canZoomOutEvenMore =
                                directRender.postViewport.virtualWidth > virtualWidth * 2f &&
                                        directRender.postViewport.virtualHeight > virtualHeight * 2f
                            if (canZoomOutEvenMore) {
                                virtualController.zoom = onLowPerformance(false)
                            }
                        }
                    }
                    fpsCheckTimeout = 5000f
                    framesRenderedInPeriod = 0
                }
            }
        }

        onDispose(::release)
    }


    private fun update(duration: Duration, camera: Camera) {
        camera.position.x = 0f
        camera.position.y = 0f
    }

    private fun render(duration: Duration, batch: Batch) {
        if (assetsReady) {
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

            if (ghostOverlay.isActive) {
                batch.setBlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.DST_ALPHA)
                //batch.shader = assets.shader.test
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
            }

            context.gl.disable(State.BLEND)
        }

        if (!focused) {
            batch.draw(
                clickToFocus,
                offsetX,
                offsetY,
                originX = 0f,
                originY = 0f,
                width = virtualWidth.toFloat() * scale,
                height = virtualHeight.toFloat() * scale,
            )
        } else if (!assetsReady || (!extraAssetsReady && delayedOpenDoorArgs != null)) {
            batch.draw(
                loading,
                offsetX,
                offsetY,
                originX = 0f,
                originY = 0f,
                width = virtualWidth.toFloat() * scale,
                height = virtualHeight.toFloat() * scale,
            )
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
                cabinetShader.fragmentShader.uResolution.apply(
                    cabinetShader,
                    temp.set(cabinetWidth, cabinetHeight)
                )
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

        if (!focused) {
            batch.draw(
                clickToFocus,
                cabinetOffsetX,
                cabinetOffsetY,
                originX = 0f,
                originY = 0f,
                width = cabinetWidth,
                height = cabinetHeight,
            )
        } else if (!assetsReady || (!extraAssetsReady && delayedOpenDoorArgs != null)) {
            batch.draw(
                loading,
                cabinetOffsetX,
                cabinetOffsetY,
                originX = 0f,
                originY = 0f,
                width = cabinetWidth,
                height = cabinetHeight,
            )
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
        val deaths: Int = 0
    )

    companion object {
        const val PPU = 80f
        const val IPPU = 1 / 80f

        const val virtualWidth = 320//256
        const val virtualHeight = 240//224//240
        const val visibleWorldWidth = (virtualWidth / PPU).toInt()
        const val visibleWorldHeight = (virtualHeight / PPU).toInt()
        const val halfVisibleWorldWidth = visibleWorldWidth / 2f
        const val halfVisibleWorldHeight = visibleWorldHeight / 2f

        val shadowColor = MutableColor(0f, 0f, 0f, 0.25f).toFloatBits()
    }

    private class OpenDoorArgs(
        val door: String,
        val toRoom: String,
        val playerHealth: Int,
        val isMagic: Boolean,
        val deaths: Int
    )
}

val Float.screenSpacePixelPerfect: Float
    get() = this.toInt().toFloat()

val Float.pixelPerfectPosition: Float
    get() = (this * Game.PPU).toInt().toFloat() * Game.IPPU