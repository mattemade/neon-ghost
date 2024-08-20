package io.itch.mattemade.neonghost

import io.itch.mattemade.neonghost.scene.InGame
import com.littlekt.Context
import com.littlekt.ContextListener
import com.littlekt.graph.node.resource.HAlign
import com.littlekt.graphics.Camera
import com.littlekt.graphics.Color
import com.littlekt.graphics.Fonts
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.gl.BlendFactor
import com.littlekt.graphics.gl.ClearBufferMask
import com.littlekt.graphics.gl.State
import com.littlekt.input.InputMapProcessor
import com.littlekt.input.InputProcessor
import com.littlekt.input.Pointer
import com.littlekt.util.milliseconds
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.blackcat.input.bindInputs
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import io.itch.mattemade.utils.render.DirectRender
import org.jbox2d.internal.System_nanoTime
import kotlin.time.Duration

class Game(context: Context, private val onLowPerformance: () -> Unit) : ContextListener(context),
    Releasing by Self() {

    var focused = false
        set(value) {
            println("setting focus to $value")
            field = value
            if (!value && assets.isLoaded && assets.music.background.playing) {
                println("Pausing audio")
                //assets.music.background.pause()
                context.audio.suspend()
            } else if (value) {
                context.audio.resume()
            }
        }
    val assets = Assets(context, ::onAnimationEvent).releasing()
    val inputController = context.bindInputs()
    var inGame: InGame? = null
    val directRender = DirectRender(context, virtualWidth, virtualHeight, ::update, ::render)
    var offsetX = 0f
    var offsetY = 0f
    var scale = 1
    private var audioReady: Boolean = false
    private var assetsReady: Boolean = false
    private var fpsCheckTimeout = 5000f
    private var framesRenderedInPeriod = 0

    private fun restartGame() {
        inGame = InGame(context, assets, inputController, ::restartGame)
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
                    restartGame()
                }
                inGame?.updateAndRender(dt)
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
                1.5f* PPU,
                0.5f* PPU,
                align = HAlign.CENTER,
                scale = scale.toFloat()
            )
        } else {
            context.gl.enable(State.BLEND)
            batch.setBlendFunction(BlendFactor.ONE, BlendFactor.ONE)

            inGame?.texture?.let {
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

            context.gl.disable(State.BLEND)
        }
    }

    companion object {
        const val PPU = 80f
        const val IPPU = 1 / 80f

        const val virtualWidth = 320//256
        const val virtualHeight = 240//224//240
        const val visibleWorldWidth = (virtualWidth / PPU).toInt()
        const val visibleWorldHeight = (virtualHeight / PPU).toInt()
    }
}