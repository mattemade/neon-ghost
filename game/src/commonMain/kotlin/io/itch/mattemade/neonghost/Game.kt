package io.itch.mattemade.neonghost

import com.littlekt.Context
import com.littlekt.ContextListener
import com.littlekt.graph.node.resource.HAlign
import com.littlekt.graphics.Camera
import com.littlekt.graphics.Color
import com.littlekt.graphics.Fonts
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.gl.BlendFactor
import com.littlekt.graphics.gl.ClearBufferMask
import com.littlekt.graphics.gl.State
import com.littlekt.graphics.toFloatBits
import com.littlekt.input.InputMapProcessor
import com.littlekt.input.InputProcessor
import com.littlekt.input.Pointer
import com.littlekt.util.milliseconds
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.blackcat.input.bindInputs
import io.itch.mattemade.neonghost.character.rei.Player
import io.itch.mattemade.neonghost.scene.GhostOverlay
import io.itch.mattemade.neonghost.scene.InGame
import io.itch.mattemade.neonghost.tempo.Choreographer
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import io.itch.mattemade.utils.render.DirectRender
import org.jbox2d.internal.System_nanoTime
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
    val ghostOverlay by lazy { GhostOverlay(context, assets, choreographer) }
    val directRender = DirectRender(context, virtualWidth, virtualHeight, ::update, ::render)
    var offsetX = 0f
    var offsetY = 0f
    var scale = 1
    private var audioReady: Boolean = false
    private var assetsReady: Boolean = false
    private var fpsCheckTimeout = 5000f
    private var framesRenderedInPeriod = 0

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

    private fun startGameFromLevel(level: LevelSpec, wentThrough: String?, playerHealth: Int, isMagic: Boolean) {
        inGame = InGame(
            context,
            assets,
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
                saveGame(wentThrough!!, previousRoomName!!, savedState!!.playerHealth, savedState!!.isMagic)
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
            offsetX = -scaledWidth * 0.5f
            offsetY = -scaledHeight * 0.5f
            focused = false
        }

        onRender { dt ->
            gl.clear(ClearBufferMask.COLOR_BUFFER_BIT)
            gl.clearColor(Color.CLEAR)
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
                inGame?.updateAndRender(choreographer.adjustedDt, dt)
                ghostOverlay.updateAndRender(choreographer.adjustedDt)
            }

            directRender.render(dt)

            framesRenderedInPeriod++
            fpsCheckTimeout -= dt.milliseconds
            if (fpsCheckTimeout < 0f) {
                if (framesRenderedInPeriod < 190) { // average is less than 38 fps
                    onLowPerformance()
                }
                fpsCheckTimeout = 5000f
                framesRenderedInPeriod = 0
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