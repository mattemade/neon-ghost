package com.game.template

import com.game.template.player.Player
import com.game.template.world.Floor
import com.littlekt.Context
import com.littlekt.ContextListener
import com.littlekt.file.vfs.writePixmap
import com.littlekt.graph.node.resource.HAlign
import com.littlekt.graphics.Color
import com.littlekt.graphics.Fonts
import com.littlekt.graphics.FrameBuffer
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.SpriteBatch
import com.littlekt.graphics.g2d.TextureAtlas
import com.littlekt.graphics.g2d.draw
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.graphics.g2d.tilemap.tiled.TiledObjectLayer
import com.littlekt.graphics.gl.BlendFactor
import com.littlekt.graphics.gl.ClearBufferMask
import com.littlekt.graphics.gl.State
import com.littlekt.graphics.gl.TexMagFilter
import com.littlekt.graphics.gl.TexMinFilter
import com.littlekt.graphics.slice
import com.littlekt.graphics.toFloatBits
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

class Game(context: Context, private val onLowPerformance: () -> Unit) : ContextListener(context), Releasing by Self() {

    var focused = false
        set(value) {
            println("setting focus to $focused")
            field = value
            if (!value /*&& assets.isLoaded && assets.music.background.playing*/) {
                println("Pausing audio")
                //assets.music.background.pause()
                //context.audio.suspend()
            } else if (value) {
                //context.audio.resume()
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

        Player(Vec2(100f, 100f), world, assets, inputController, assets.objects.particleSimulator, context.vfs)
    }

    private var audioReady = false
    val opaqueYellow = MutableColor(Color.YELLOW).also { it.a = 0.5f }
    private var fpsCheckTimeout = 5000f
    private var framesRenderedInPeriod = 0

    override suspend fun Context.start() {
        val batch = SpriteBatch(context).releasing()
        val postBatch = SpriteBatch(context).releasing()
        val shapeRenderer = ShapeRenderer(batch)
        val postShapeRenderer = ShapeRenderer(postBatch)
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
        val postViewport = ScalingViewport(scaler = Scaler.Fit(), virtualWidth, virtualHeight)
        val postCamera = postViewport.camera
        var rotation = 0.radians
        var rotationTimer = 0.milliseconds
        val bpm = 138.6882f//128.5714f
        val secondsPerBeat = 60f / bpm
        val doubleSecondsPerBeat = secondsPerBeat * 2f
        var time = 0f - 0.2f

        var wasFocused = false

        input.addInputProcessor(object : InputProcessor {
            override fun touchUp(screenX: Float, screenY: Float, pointer: Pointer): Boolean {
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
            println("scaling $width $height to something between $widthScale $heightScale")
            scale = minOf(widthScale, heightScale)
            val scaledWidth = virtualWidth * scale
            val scaledHeight = virtualHeight * scale

            postViewport.virtualWidth = width.toFloat()
            postViewport.virtualHeight = height.toFloat()
            postViewport.update(width, height, context)
            offsetX = -scaledWidth * 0.5f
            offsetY = -scaledHeight * 0.5f
            println("Resized to $width $height, scaled to $scaledWidth $scaledHeight, offset $offsetX $offsetY")

            focused = false
        }

        onRender { dt ->
            gl.clear(ClearBufferMask.COLOR_BUFFER_BIT)
            gl.clearColor(Color.CLEAR)
            if (!audioReady) {
                audioReady = audio.isReady()
            }
            val assetsReady = audioReady && assets.isLoaded
            if (assetsReady) {

                if (focused) {
                    time += dt.seconds
                    /*if (!assets.music.background.playing && !wasFocused) {
                        audio.setListenerPosition(virtualWidth / 2f, virtualHeight / 2f, -200f)
                        vfs.launch { assets.music.background.play(volume = 0.1f, loop = true) }
                    }*/
                    if (!wasFocused) {
                        vfs.launch {
                            vfs["atlas2.png"].writePixmap(assets.tileSets.wall.texture.textureData.pixmap)
                        }
                    }
                    wasFocused = true
                }
            }
            val millis = dt.milliseconds

            target.begin()
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
                assets.level.testRoom.render(batch, targetCamera)
                player.update(dt, millis)
                assets.objects.particleSimulator.update(dt)
                world.step(dt.seconds, 6, 2)
                player.draw(batch)

                if (time % doubleSecondsPerBeat < secondsPerBeat) {
                    opaqueYellow.set(Color.RED)
                } else {
                    opaqueYellow.set(Color.GREEN)
                }
                opaqueYellow.a = 1f - (time % secondsPerBeat) / secondsPerBeat

                gl.enable(State.BLEND)
                batch.setBlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA)
                assets.objects.particleSimulator.draw(batch)
                shapeRenderer.filledRectangle(
                    virtualWidth/2f,
                    0f,
                    100f,
                    50f,
                    rotation,
                    color = opaqueYellow.toFloatBits()
                )
                batch.setToPreviousBlendFunction()
                gl.disable(State.BLEND)
            }
            batch.flush()
            batch.end()
            target.end()



            postViewport.apply(context)
            postCamera.update()
            postBatch.begin(postCamera.viewProjection)
            //gl.disable(State.BLEND)
            gl.enable(State.BLEND)
            postBatch.setBlendFunction(BlendFactor.ONE, BlendFactor.ONE)

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
            postShapeRenderer.circle(x = 0f ,y = 0f, radius = 10f, thickness = 3, color = Color.BLUE.toFloatBits())

            gl.enable(State.BLEND)
            postBatch.setBlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA)
            postShapeRenderer.filledRectangle(
                0f,
                0f,
                1000f,
                500f,
                rotation,
                color = opaqueYellow.toFloatBits()
            )
            postBatch.setToPreviousBlendFunction()
            gl.disable(State.BLEND)
            postBatch.flush()
            postBatch.end()

            rotationTimer += dt
            if (rotationTimer > 10.milliseconds) {
                rotationTimer = 0.milliseconds
                rotation += 1.degrees
            }

            framesRenderedInPeriod++
            fpsCheckTimeout -= millis
            if (fpsCheckTimeout < 0f) {
                if (framesRenderedInPeriod < 190) { // average is less than 38 fps
                    onLowPerformance()
                }
                fpsCheckTimeout = 5000f
                framesRenderedInPeriod = 0
            }
        }

        onDispose(::release)
    }
}