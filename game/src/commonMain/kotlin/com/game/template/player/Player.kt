package com.game.template.player

import com.game.template.Assets
import com.littlekt.graphics.Color
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.PixmapTexture
import com.littlekt.graphics.g2d.ParticleSimulator
import com.littlekt.graphics.g2d.SpriteBatch
import com.littlekt.input.InputMapController
import com.littlekt.math.random
import com.littlekt.resources.Textures
import com.littlekt.util.seconds
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.utils.animation.SignallingAnimationPlayer
import io.itch.mattemade.utils.releasing.HasContext
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Body
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import kotlin.time.Duration

class Player(
    initialPosition: Vec2,
    private val world: World,
    private val assets: Assets,
    private val controller: InputMapController<GameInput>,
    private val particleSimulator: ParticleSimulator,
) : Releasing by Self(),
    HasContext<Body> {

    private val textureSizeInWorldUnits =
        Vec2(444f * 16f / 320f, 366f * 9f / 240f).mulLocal(2f) // 3.7 x 3.05
    val pixelWidth = 444f / textureSizeInWorldUnits.x
    val pixelWidthInt = pixelWidth.toInt()
    val pixelHeight = 366f / textureSizeInWorldUnits.y
    val pixelHeightInt = pixelHeight.toInt()
    private val physicalHw = 1f
    private val physicalHh = 1f


    private val body = world.createBody(
        BodyDef(
            type = BodyType.DYNAMIC,
            userData = this,
        ).apply {
            position.set(initialPosition)
        }
    )

    val x get() = body.position.x
    val y get() = body.position.y

    private var currentAnimation: SignallingAnimationPlayer = assets.playerAnimations.idle
        set(value) {
            value.restart()
            field = value
        }

    private val fixture = body.createFixture(
        FixtureDef(
            shape = PolygonShape().apply {
                setAsBox(physicalHw, physicalHh)
            },
            /*filter = Filter().apply {
                categoryBits = ContactBits.CAT_BIT
                maskBits = ContactBits.BLOCK_BIT or ContactBits.PLATFORM_BIT or ContactBits.WALL_BIT
            },*/
            friction = 2f,
            userData = this
        )
    ) ?: error("Cat fixture is null! Should not happen!")

    override val context: Map<Any, Body> = mapOf(Body::class to body)

    private val tempVec2 = Vec2()
    private val zeroVec2 = Vec2()
    private val tempColor = MutableColor()

    fun update(dt: Duration, millis: Float) {
        val xMovement = controller.axis(GameInput.HORIZONTAL)
        val yMovement = -controller.axis(GameInput.VERTICAL)
        body.linearVelocity.set(xMovement * 100f * millis, yMovement * 100f * millis)
        body.isAwake = true
        currentAnimation.update(dt)

        if (controller.pressed(GameInput.ATTACK) || controller.pressed(GameInput.JUMP) || controller.justTouched) {
            currentAnimation.currentKeyFrame?.let {
                if (it is PixmapTexture) {
                    val xOffset = texturePositionX()
                    val yOffset = texturePositionY()
                    val midHeight = it.height / 2
                    var firstMeaningfulX = 0
                    for (x in 0 until it.width step pixelWidthInt) {
                        for (y in 0 until it.height step pixelHeightInt) {
                            val pixelColor = it.pixmap.get(x, y)
                            if (pixelColor != 0) {
                                if (firstMeaningfulX == 0) {
                                    firstMeaningfulX = x
                                }
                                particleSimulator.alloc(Textures.white, xOffset + x/pixelWidth, yOffset - y/pixelHeight)
                                    .apply {
                                        //alpha = 1f
                                        scale(1f)
                                        delay = ((x - firstMeaningfulX) / 750f + 1f).seconds
                                        color.setArgb888(pixelColor)
                                        xDelta = 0.4f + (-0.25f..0.25f).random()
                                        yDelta = (midHeight - y) / 500f + (-0.45f..0.45f).random()//0f//-(-1.15f..1.15f).random()
                                        //alphaDelta = 0.5f
                                        life = 4f.seconds
                                        //scaleDelta = 0f
                                        friction = 0.97f
                                        //fadeOutSpeed = 0.01f
                                        alphaDelta = -0.015f
                                        /*onUpdate = {
                                            it.color.a /= 1.05f
                                        }*/
                                    }
                            }
                        }
                    }

                }
            }

        }
    }

    fun draw(batch: SpriteBatch) {
        currentAnimation.currentKeyFrame?.let { frame ->
            batch.draw(
                frame,
                texturePositionX(),
                texturePositionY(),
                width = textureSizeInWorldUnits.x,
                height = textureSizeInWorldUnits.y,
                flipX = false
            )
        }
    }

    private fun texturePositionX() = body.position.x - physicalHw - textureSizeInWorldUnits.x / 2f
    private fun texturePositionY() = body.position.y - physicalHh
}

private fun MutableColor.setArgb888(argb8888: Int):MutableColor {
    a = ((argb8888 and 0xff000000.toInt()) ushr 24) / 255f
    b = ((argb8888 and 0x00ff0000) ushr 16) / 255f
    g = ((argb8888 and 0x0000ff00) ushr 8) / 255f//?
    r = (argb8888 and 0x000000ff) / 255f //?
    return this
}
