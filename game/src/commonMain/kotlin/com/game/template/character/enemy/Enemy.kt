package com.game.template.character.enemy

import com.game.template.Assets
import com.game.template.Game
import com.game.template.character.DepthBasedRenderable
import com.game.template.character.rei.Player
import com.game.template.world.ContactBits
import com.littlekt.file.Vfs
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.Textures
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.ParticleSimulator
import com.littlekt.graphics.gl.PixmapTextureData
import com.littlekt.input.InputMapController
import com.littlekt.math.MutableVec2f
import com.littlekt.math.Vec2f
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
import org.jbox2d.dynamics.Filter
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import kotlin.time.Duration

class Enemy(
    initialPosition: Vec2,
    private val player: Player,
    private val world: World,
    private val assets: Assets,
    private val controller: InputMapController<GameInput>,
    private val particleSimulator: ParticleSimulator,
    private val vfs: Vfs
) : Releasing by Self(),
    HasContext<Body>,
    DepthBasedRenderable {

    private val textureSizeInWorldUnits = Vec2(60f / Game.PPU, 96f / Game.PPU)
    val pixelWidth = 1f//textureSizeInWorldUnits.x
    val pixelWidthInt = pixelWidth.toInt()
    val pixelHeight = 1f//textureSizeInWorldUnits.y
    val pixelHeightInt = pixelHeight.toInt()
    private val physicalHw = 1f / Game.PPU
    private val physicalHh = 1f / Game.PPU


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
    override val depth: Float get() = y

    private var currentMagicalAnimation: SignallingAnimationPlayer =
        assets.magicalReiAnimations.walk
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
            filter = Filter().apply {
                categoryBits = ContactBits.ENEMY
                maskBits = ContactBits.WALL or ContactBits.REI_PUNCH
            },
            friction = 2f,
            userData = this
        )
    ) ?: error("Cat fixture is null! Should not happen!")

    override val context: Map<Any, Body> = mapOf(Body::class to body)

    private val tempVec2 = Vec2()
    private val tempVec2f = MutableVec2f()
    private val tempVec2f2 = MutableVec2f()
    private val zeroVec2 = Vec2()
    private val tempColor = MutableColor()

    private var isFacingLeft = false
    private var wasPunching = false
    private var nextLeftPunch = true
    private var punchCooldown = 0f
    private var hitCooldown = 0f

    fun hit(from: Vec2) {
        hitCooldown = 300f
        val direction = tempVec2f.set(body.position.x, body.position.y).subtract(from.x, from.y)
        tempVec2f2.set(1f, 0f)
        val distance = direction.length()
        val rotation = direction.angleTo(tempVec2f2)
        println("distance: $distance")
        //tempVec2f2.scale(distance).rotate(rotation)
        tempVec2.set(tempVec2f2.x, tempVec2f2.y)
        println("move: $tempVec2")
        body.linearVelocity.set(tempVec2)
        body.isAwake = true
        //body.applyLinearImpulse(tempVec2, body.position, true)
    }

    override fun update(dt: Duration, millis: Float, toBeat: Float) {
        if (punchCooldown > 0f) {
            body.linearVelocity.set(0f, 0f)
            punchCooldown -= millis
            if (punchCooldown > 0f) {
                currentMagicalAnimation.update(dt)
                return
            }
        }
        if (hitCooldown > 0f) {
            hitCooldown -= millis
            return
        }
        val inverseBeat = 1f - toBeat
        val direction = tempVec2.set(player.x - x, player.y - y)
        val distance = tempVec2.length()
        tempVec2
            .mulLocal(millis)
            .mulLocal(inverseBeat * inverseBeat * inverseBeat)
            .mulLocal(0.1f)

        val speed = direction.length() / 100f * Game.PPU

        if (direction.x != 0f) {
            if (isFacingLeft && direction.x > 0f) {
                isFacingLeft = false
            } else if (!isFacingLeft && direction.x < 0f) {
                isFacingLeft = true
            }
        }

        if (direction.x == 0f && direction.y == 0f || distance < 0.5f) {
            currentMagicalAnimation = assets.magicalReiAnimations.idle
            body.linearVelocity.set(0f, 0f)
        } else if (speed != 0f) {
            currentMagicalAnimation = assets.magicalReiAnimations.walk
            body.linearVelocity.set(direction)
            body.isAwake = true
        }
        currentMagicalAnimation.update(dt * speed.toDouble() )
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
        currentMagicalAnimation.currentKeyFrame?.let { slice ->
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

    override fun render(batch: Batch) {
        currentMagicalAnimation.currentKeyFrame?.let { frame ->
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
    }

    private fun texturePositionX() = body.position.x - physicalHw - textureSizeInWorldUnits.x / 2f
    private fun texturePositionY() = body.position.y - physicalHh - textureSizeInWorldUnits.y
}