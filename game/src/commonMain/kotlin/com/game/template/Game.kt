package com.game.template

import com.game.template.player.Player
import com.game.template.shader.Crt
import com.game.template.world.Floor
import com.littlekt.Context
import com.littlekt.ContextListener
import com.littlekt.graphics.Color
import com.littlekt.graphics.EmptyTexture
import com.littlekt.graphics.HAlign
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.ParticleSimulator
import com.littlekt.graphics.g2d.SpriteBatch
import com.littlekt.graphics.g2d.draw
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.graphics.g2d.tilemap.tiled.TiledObjectLayer
import com.littlekt.graphics.shader.Shader
import com.littlekt.graphics.shader.SpriteShader
import com.littlekt.graphics.webgpu.AlphaMode
import com.littlekt.graphics.webgpu.BindGroupLayoutDescriptor
import com.littlekt.graphics.webgpu.BindGroupLayoutEntry
import com.littlekt.graphics.webgpu.BufferBindingLayout
import com.littlekt.graphics.webgpu.LoadOp
import com.littlekt.graphics.webgpu.PresentMode
import com.littlekt.graphics.webgpu.RenderPassColorAttachmentDescriptor
import com.littlekt.graphics.webgpu.RenderPassDescriptor
import com.littlekt.graphics.webgpu.SamplerBindingLayout
import com.littlekt.graphics.webgpu.ShaderStage
import com.littlekt.graphics.webgpu.StoreOp
import com.littlekt.graphics.webgpu.TextureBindingLayout
import com.littlekt.graphics.webgpu.TextureStatus
import com.littlekt.graphics.webgpu.TextureUsage
import com.littlekt.input.InputProcessor
import com.littlekt.input.Pointer
import com.littlekt.math.geom.degrees
import com.littlekt.math.geom.radians
import com.littlekt.resources.Fonts
import com.littlekt.util.Scaler
import com.littlekt.util.align
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
    private val virtualHeight = 224//240

    private val floors = mutableListOf<Floor>()
    private val world by lazy { World(gravityX = 0f, gravityY = 0f).apply {
        assets.level.testRoom.layers.asSequence().filterIsInstance<TiledObjectLayer>().forEach {
            if (it.name == "floor") {
                it.objects.forEach {
                    floors += Floor(this, it.bounds)
                }
            }
        }
    } }
    private val player by lazy {

        Player(Vec2(100f, 100f), world, assets, inputController, assets.objects.particleSimulator)
    }

    val opaqueYellow = MutableColor(Color.YELLOW).also { it.a = 0.5f }

    override suspend fun Context.start() {

        val device = graphics.device.releasing()
        val surfaceCapabilities = graphics.surfaceCapabilities
        val preferredFormat = graphics.preferredFormat
        val target = EmptyTexture(device, preferredFormat, virtualWidth, virtualHeight)

        graphics.configureSurface(
            TextureUsage.RENDER_ATTACHMENT,
            preferredFormat,
            PresentMode.FIFO,
            surfaceCapabilities.alphaModes[0]
        )
        val batch = SpriteBatch(device, graphics, preferredFormat).releasing()
        val postBatch = SpriteBatch(device, graphics, preferredFormat).releasing()
        postBatch.shader = Crt(device)
        val shapeRenderer = ShapeRenderer(batch)
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
            surfaceViewport.update(width, height)
            offsetX = -scaledWidth * 0.5f
            offsetY = -scaledHeight * 0.5f

            graphics.configureSurface(
                TextureUsage.RENDER_ATTACHMENT,
                preferredFormat,
                PresentMode.FIFO,
                surfaceCapabilities.alphaModes[0]
            )
            focused = false


        }
        onUpdate { dt ->
            val surfaceTexture = graphics.surface.getCurrentTexture()
            when (val status = surfaceTexture.status) {
                TextureStatus.SUCCESS -> {
                    // all good, could check for `surfaceTexture.suboptimal` here.
                }

                TextureStatus.TIMEOUT,
                TextureStatus.OUTDATED,
                TextureStatus.LOST -> {
                    surfaceTexture.texture?.release()
                    logger.info { "getCurrentTexture status=$status" }
                    return@onUpdate
                }

                else -> {
                    // fatal
                    logger.fatal { "getCurrentTexture status=$status" }
                    close()
                    return@onUpdate
                }
            }


            val assetsReady = assets.isLoaded
            if (assetsReady) {
                if (focused) {
                    time += dt.seconds
                    if (!assets.music.background.playing) {
                        if (!wasFocused) {
                            println("Playing")
                            vfs.launch {
                                assets.music.background.play(volume = 0.1f, loop = true, onCompletion = { println("Finished") })
                            }
                        } else {
                            println("Resuming")
                            assets.music.background.resume()
                        }
                    }
                    wasFocused = true
                }
            }


            val swapChainTexture = checkNotNull(surfaceTexture.texture)
            val frame = swapChainTexture.createView()

            val commandEncoder = device.createCommandEncoder()

            val renderTargetRenderPass =
                commandEncoder.beginRenderPass(
                    RenderPassDescriptor(
                        listOf(
                            RenderPassColorAttachmentDescriptor(
                                view = target.view,
                                loadOp = LoadOp.CLEAR,
                                storeOp = StoreOp.STORE,
                                clearColor =
                                if (preferredFormat.srgb) Color.YELLOW.toLinear()
                                else Color.YELLOW
                            )
                        ),
                        label = "Target render pass"
                    )
                )

            targetViewport.apply()
            targetCamera.position.x = virtualWidth / 2f
            targetCamera.position.y = virtualHeight / 2f
            targetCamera.update()
            batch.begin(targetCamera.viewProjection)
            if (!focused) {
                Fonts.default.draw(batch, "CLICK TO FOCUS", 120f, 40f, align = HAlign.CENTER)
            } else if (!assetsReady) {
                Fonts.default.draw(batch, "LOADING", 120f, 40f, align = HAlign.CENTER)
            } else {
                val millis = dt.milliseconds
                assets.level.testRoom.render(batch, targetCamera)
                floors.forEach {
                    //shapeRenderer.path(it.renderPath, color = opaqueYellow)
                    shapeRenderer.rectangle(it.rect.x, it.rect.y - it.rect.height, it.rect.width, it.rect.height, color = opaqueYellow)
                }
                player.update(dt, millis)
                assets.objects.particleSimulator.update(dt)
                world.step(dt.seconds, 6, 2)
                player.draw(batch)
                assets.objects.particleSimulator.draw(batch)
                shapeRenderer.filledRectangle(
                    -50f,
                    50f,
                    100f,
                    50f,
                    rotation,
                    color = if (time % doubleSecondsPerBeat < secondsPerBeat) Color.RED else Color.GREEN
                )
            }
            batch.flush(renderTargetRenderPass)
            batch.end()
            renderTargetRenderPass.end()

            val renderPassEncoder =
                commandEncoder.beginRenderPass(
                    desc =
                    RenderPassDescriptor(
                        listOf(
                            RenderPassColorAttachmentDescriptor(
                                view = frame,
                                loadOp = LoadOp.CLEAR,
                                storeOp = StoreOp.STORE,
                                clearColor =
                                if (preferredFormat.srgb) Color.BLACK.toLinear()
                                else Color.BLACK
                            )
                        ),
                        label = "Surface render pass",
                    )

                )

            surfaceViewport.apply()
            surfaceCamera.update()
            postBatch.begin(surfaceCamera.viewProjection)
            postBatch.draw(
                target,
                x = offsetX,
                y = offsetY,
                originX = 0f,
                originY = 0f,
                width = virtualWidth.toFloat() * scale,
                height = virtualHeight.toFloat() * scale,
            )
            postBatch.flush(renderPassEncoder)
            postBatch.end()
            renderPassEncoder.end()

            rotationTimer += dt
            if (rotationTimer > 10.milliseconds) {
                rotationTimer = 0.milliseconds
                rotation += 1.degrees
            }

            val commandBuffer = commandEncoder.finish()

            device.queue.submit(commandBuffer)
            graphics.surface.present()

            commandBuffer.release()
            renderPassEncoder.release()
            commandEncoder.release()
            frame.release()
            swapChainTexture.release()
        }

        onRelease(::release)
    }
}