package com.game.template

import com.game.template.player.Player
import com.game.template.world.Floor
import com.littlekt.Context
import com.littlekt.ContextListener
import com.littlekt.graph.node.resource.HAlign
import com.littlekt.graphics.Color
import com.littlekt.graphics.Fonts
import com.littlekt.graphics.FrameBuffer
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.SpriteBatch
import com.littlekt.graphics.g2d.draw
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.graphics.g2d.tilemap.tiled.TiledObjectLayer
import com.littlekt.graphics.gl.BlendFactor
import com.littlekt.graphics.gl.ClearBufferMask
import com.littlekt.graphics.gl.State
import com.littlekt.graphics.gl.TexMagFilter
import com.littlekt.graphics.gl.TexMinFilter
import com.littlekt.graphics.shader.Shader
import com.littlekt.graphics.slice
import com.littlekt.graphics.toFloatBits
import com.littlekt.graphics.util.BlendMode
import com.littlekt.input.InputProcessor
import com.littlekt.input.Pointer
import com.littlekt.math.geom.degrees
import com.littlekt.math.geom.radians
import com.littlekt.util.Scaler
import com.littlekt.util.milliseconds
import com.littlekt.util.seconds
import com.littlekt.util.viewport.ScalingViewport
import io.itch.mattemade.blackcat.input.bindInputs
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.World
import kotlin.time.Duration.Companion.milliseconds

class Game(context: Context) : ContextListener(context), Releasing by Self() {

    var focused = false
        set(value) {
            field = value
            if (!value && assets.isLoaded && assets.music.background.playing) {
                assets.music.background.pause()
            }
        }
    val assets = Assets(context).releasing()
    val inputController = context.bindInputs()


    private val virtualWidth = 320//256
    private val virtualHeight = 240//224//240

    private val floors = mutableListOf<Floor>()
    private val world by lazy { World(gravityX = 0f, gravityY = 0f).apply {
        val level = assets.level.testRoom
        val mapHeight = level.height * level.tileHeight
        level.layers.asSequence().filterIsInstance<TiledObjectLayer>().forEach {
            if (it.name == "floor") {
                it.objects.forEach {
                    floors += Floor(this, it.bounds, mapHeight)
                }
            }
        }
    } }
    private val player by lazy {

        Player(Vec2(100f, 100f), world, assets, inputController, assets.objects.particleSimulator)
    }

    val opaqueYellow = MutableColor(Color.YELLOW).also { it.a = 0.5f }

    override suspend fun Context.start() {


        val batch = SpriteBatch(context).releasing()
        val postBatch = SpriteBatch(context).releasing()
        val shapeRenderer = ShapeRenderer(batch)
        val target = FrameBuffer(
            virtualWidth,
            virtualHeight,
            listOf(FrameBuffer.TextureAttachment(minFilter = TexMinFilter.NEAREST, magFilter = TexMagFilter.NEAREST))
        ).also {
            it.prepare(context)
        }
        val targetSlice = target.textures[0].slice()
        val targetViewport = ScalingViewport(scaler = Scaler.Fit(), virtualWidth, virtualHeight)
        val targetCamera = targetViewport.camera
        val surfaceViewport = ScalingViewport(scaler = Scaler.Fit(), virtualWidth, virtualHeight)
        val surfaceCamera = surfaceViewport.camera
        var rotation = 0.radians
        var rotationTimer = 0.milliseconds
        val bpm = 140f//128.5714f
        val secondsPerBeat = 60f / bpm
        val doubleSecondsPerBeat = secondsPerBeat * 2f
        var time = 0f

        var wasFocused = false

        input.addInputProcessor(object : InputProcessor {
            override fun touchDown(screenX: Float, screenY: Float, pointer: Pointer): Boolean {
                if (focused) {

                } else {
                    focused = true
                }
                return true
            }
        })

        var offsetX = 0f
        var offsetY = 0f
        var scale = 1
        onResize { width, height ->
            val widthScale = width / virtualWidth
            val heightScale = height / virtualHeight
            println("selecting between $widthScale $heightScale")
            scale = minOf(widthScale, heightScale)
            val scaledWidth = virtualWidth * scale
            val scaledHeight = virtualHeight * scale
            //targetViewport.update(scaledWidth, scaledHeight)

            surfaceViewport.virtualWidth = width.toFloat()
            surfaceViewport.virtualHeight = height.toFloat()
            surfaceViewport.update(width, height, context)
            offsetX = -scaledWidth * 0.5f
            offsetY = -scaledHeight * 0.5f

            focused = false


        }
        onRender { dt ->

            val assetsReady = assets.isLoaded
            if (assetsReady) {
                if (focused) {
                    time += dt.seconds
                    if (!assets.music.background.playing) {
                        if (!wasFocused) {
                            println("Playing")
                            vfs.launch {
                                assets.music.background.play(volume = 0.1f, loop = true)
                            }
                        } else {
                            println("Resuming")
                            assets.music.background.resume()
                        }
                    }
                    wasFocused = true
                }
            }

            target.begin()
            gl.clearColor(Color.BLACK)
            gl.clear(ClearBufferMask.COLOR_BUFFER_BIT)
            //gl.enable(State.BLEND)
            targetViewport.apply(context)
            targetCamera.position.x = virtualWidth / 2f
            targetCamera.position.y = virtualHeight / 2f
            targetCamera.update()
            batch.begin(targetCamera.viewProjection)
            //batch.setBlendFunction(BlendMode.Alpha)
            if (!focused) {
                Fonts.default.draw(batch, "CLICK TO FOCUS", 120f, 40f, align = HAlign.CENTER)
            } else if (!assetsReady) {
                Fonts.default.draw(batch, "LOADING", 120f, 40f, align = HAlign.CENTER)
            } else {
                val millis = dt.milliseconds
                assets.level.testRoom.render(batch, targetCamera)
                floors.forEach {
                    //shapeRenderer.path(it.renderPath, color = opaqueYellow)
                    shapeRenderer.rectangle(it.rect.x, it.rect.y - it.rect.height, it.rect.width, it.rect.height, color = opaqueYellow.toFloatBits())
                }
                player.update(dt, millis)
                assets.objects.particleSimulator.update(dt)
                world.step(dt.seconds, 6, 2)
                player.draw(batch)

                //gl.enable(State.BLEND)
                //batch.setBlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA)
                assets.objects.particleSimulator.draw(batch)
                //batch.setToPreviousBlendFunction()
                //gl.disable(State.BLEND)

                shapeRenderer.filledRectangle(
                    -50f,
                    50f,
                    100f,
                    50f,
                    rotation,
                    color = (if (time % doubleSecondsPerBeat < secondsPerBeat) Color.RED else Color.GREEN).toFloatBits()
                )
            }
            batch.flush()
            batch.end()
            target.end()


            gl.clearColor(Color.BLACK)
            gl.clear(ClearBufferMask.COLOR_BUFFER_BIT)

            surfaceViewport.apply(context)
            surfaceCamera.update()
            postBatch.begin(surfaceCamera.viewProjection)
            postBatch.draw(
                targetSlice,
                x = offsetX,
                y = offsetY,
                originX = 0f,
                originY = 0f,
                width = virtualWidth.toFloat() * scale,
                height = virtualHeight.toFloat() * scale,
                flipY = true,
            )
            postBatch.flush()
            postBatch.end()

            rotationTimer += dt
            if (rotationTimer > 10.milliseconds) {
                rotationTimer = 0.milliseconds
                rotation += 1.degrees
            }

        }

        onDispose(::release)
    }
}