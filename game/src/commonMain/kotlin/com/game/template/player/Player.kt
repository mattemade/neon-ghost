package com.game.template.player

import com.game.template.Assets
import com.littlekt.file.Vfs
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.Textures
import com.littlekt.graphics.g2d.ParticleSimulator
import com.littlekt.graphics.g2d.SpriteBatch
import com.littlekt.graphics.gl.PixmapTextureData
import com.littlekt.input.InputMapController
import com.littlekt.math.random
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
    private val vfs: Vfs
) : Releasing by Self(),
    HasContext<Body> {

    private val textureSizeInWorldUnits = Vec2(60f, 96f)
    val pixelWidth = 1f//textureSizeInWorldUnits.x
    val pixelWidthInt = pixelWidth.toInt()
    val pixelHeight = 1f//textureSizeInWorldUnits.y
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

    private var currentAnimation: SignallingAnimationPlayer = assets.normalReiAnimations.walk
        set(value) {
            if (field != value) {
                value.restart()
                field = value
            }
        }
    private var currentMagicalAnimation: SignallingAnimationPlayer = assets.magicalReiAnimations.walk
        set(value) {
            if (field != value) {
                value.restart()
                field = value
            }
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

    private var isFacingLeft = false
    private var wasPunching = false
    private var nextLeftPunch = true
    private var punchCooldown = 0f

    fun update(dt: Duration, millis: Float) {
        if (punchCooldown > 0f) {
            body.linearVelocity.set(0f, 0f)
            punchCooldown -= millis
            if (punchCooldown > 0f) {
                currentAnimation.update(dt)
                currentMagicalAnimation.update(dt)
                return
            }
        }
        val xMovement = controller.axis(GameInput.HORIZONTAL)
        val yMovement = controller.axis(GameInput.VERTICAL)
        if (xMovement != 0f || yMovement != 0f) {
            wasPunching = false
            nextLeftPunch = true
            currentAnimation = assets.normalReiAnimations.walk
            currentMagicalAnimation = assets.magicalReiAnimations.walk
            if (isFacingLeft && xMovement > 0f) {
                isFacingLeft = false
            } else if (!isFacingLeft && xMovement < 0f) {
                isFacingLeft = true
            }
        } else if (!wasPunching) {
            currentAnimation = assets.normalReiAnimations.idle
            currentMagicalAnimation = assets.magicalReiAnimations.idle
        }
        body.linearVelocity.set(xMovement * 100f * millis, yMovement * 100f * millis)
        body.isAwake = true

        if (controller.pressed(GameInput.ATTACK) || controller.pressed(GameInput.JUMP) || controller.justTouched) {
            punchCooldown = if (wasPunching) 600f else 900f
            currentAnimation = if (nextLeftPunch) {
                if (wasPunching) {
                    assets.normalReiAnimations.quickLeftPunch
                } else {
                    assets.normalReiAnimations.leftPunch
                }
            } else {
                if (wasPunching) {
                    assets.normalReiAnimations.quickRightPunch
                } else {
                    assets.normalReiAnimations.rightPunch
                }
            }
            currentMagicalAnimation = if (nextLeftPunch) {
                if (wasPunching) {
                    assets.magicalReiAnimations.quickLeftPunch
                } else {
                    assets.magicalReiAnimations.leftPunch
                }
            } else {
                if (wasPunching) {
                    assets.magicalReiAnimations.quickRightPunch
                } else {
                    assets.magicalReiAnimations.rightPunch
                }
            }
            wasPunching = true
            nextLeftPunch = !nextLeftPunch
            activateParticles()
        }
        currentAnimation.update(dt)
        currentMagicalAnimation.update(dt)
    }

    private fun activateParticles() {
        println("position: ${body.position.x}")
        /*assets.sound.wind.play(
            volume = 0.5f,
            positionX = body.position.x,
            positionY = 0f,
            referenceDistance = 200f,
            rolloffFactor = 0.01f
        )*/
        currentAnimation.currentKeyFrame?.let { slice ->
            val textureData = slice.texture.textureData
            if (textureData is PixmapTextureData) {
                val xOffset = texturePositionX()
                val yOffset = texturePositionY()
                val midHeight = slice.height / 2
                var firstMeaningfulX = 0
                val width = slice.x + slice.width
                val height = slice.y + slice.height
                for (sliceX in slice.x until width step pixelWidthInt) {
                    for (sliceY in slice.y until height step pixelHeightInt) {
                        val x = sliceX - slice.x
                        val y = sliceY - slice.y
                        val pixelColor = textureData.pixmap.get(x, y)
                        if (pixelColor != 0) {
                            if (firstMeaningfulX == 0) {
                                firstMeaningfulX = x
                            }
                            particleSimulator.alloc(
                                Textures.white,
                                xOffset + textureSizeInWorldUnits.x * 2 - x / pixelWidth,
                                yOffset + y / pixelHeight
                            )
                                .apply {
                                    //alpha = 1f
                                    scale(1f)
                                    delay =
                                        ((textureSizeInWorldUnits.x - x) / 7.5f + 1f + (-0.2f..0.2f).random()).seconds
                                    color.setRgba8888(pixelColor)
                                    xDelta = 0.4f + (-0.25f..0.25f).random()
                                    yDelta =
                                        (midHeight - y) / 500f + (-0.45f..0.45f).random()//0f//-(-1.15f..1.15f).random()
                                    //alphaDelta = 0.5f
                                    life = 4f.seconds
                                    //scaleDelta = 0f
                                    friction = 1.03f
                                    //fadeOutSpeed = 0.01f
                                    alphaDelta = -0.005f
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

    fun draw(batch: SpriteBatch) {
        currentAnimation.currentKeyFrame?.let { frame ->
            val positionX = texturePositionX()
            val positionY = texturePositionY()
            //println("player at $positionX, $positionY")
            batch.draw(
                frame,
                positionX,
                positionY,
                width = textureSizeInWorldUnits.x,
                height = textureSizeInWorldUnits.y,
                flipX = isFacingLeft
            )
        }
        currentMagicalAnimation.currentKeyFrame?.let { frame ->
            val positionX = texturePositionX() + textureSizeInWorldUnits.x*2f
            val positionY = texturePositionY()
            //println("player at $positionX, $positionY")
            batch.draw(
                frame,
                positionX,
                positionY,
                width = textureSizeInWorldUnits.x,
                height = textureSizeInWorldUnits.y,
                flipX = isFacingLeft
            )
        }
    }

    private fun texturePositionX() = body.position.x - physicalHw - textureSizeInWorldUnits.x / 2f
    private fun texturePositionY() = body.position.y - physicalHh - textureSizeInWorldUnits.y
}

private fun MutableColor.setArgb888(argb8888: Int):MutableColor {
    a = ((argb8888 and 0xff000000.toInt()) ushr 24) / 255f
    b = ((argb8888 and 0x00ff0000) ushr 16) / 255f
    g = ((argb8888 and 0x0000ff00) ushr 8) / 255f//?
    r = (argb8888 and 0x000000ff) / 255f //?
    return this
}
