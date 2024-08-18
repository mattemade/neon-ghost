package com.game.template.character.rei

import com.game.template.Assets
import com.game.template.Game
import com.game.template.character.DepthBasedRenderable
import com.game.template.character.enemy.Enemy
import com.game.template.world.ContactBits
import com.littlekt.file.Vfs
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.Textures
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.ParticleSimulator
import com.littlekt.graphics.gl.PixmapTextureData
import com.littlekt.input.InputMapController
import com.littlekt.math.random
import com.littlekt.util.seconds
import com.soywiz.korma.geom.radians
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

class Player(
    initialPosition: Vec2,
    private val world: World,
    private val assets: Assets,
    private val controller: InputMapController<GameInput>,
    private val particleSimulator: ParticleSimulator,
    private val vfs: Vfs
) : Releasing by Self(),
    HasContext<Body>,
    DepthBasedRenderable {

    //private val textureSizeInWorldUnits = Vec2(60f / Game.PPU, 96f / Game.PPU)
    val pixelWidth = 1f//textureSizeInWorldUnits.x
    val pixelWidthInt = pixelWidth.toInt()
    val pixelHeight = 1f//textureSizeInWorldUnits.y
    val pixelHeightInt = pixelHeight.toInt()
    private val physicalHw = 1f / Game.PPU
    private val physicalHh = 1f / Game.PPU
    private val punchDistance = 28f / Game.PPU
    private val punchWidth = 24f / Game.PPU
    private val punchDepth = 20f / Game.PPU


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

    private val animations = assets.animation.normalReiAnimations

    internal var currentAnimation: SignallingAnimationPlayer = animations.walk
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
                categoryBits = ContactBits.REI
                maskBits = ContactBits.WALL
            },
            userData = this
        )
    ) ?: error("Fixture is null! Should not happen!")

    private val tempVec2 = Vec2()

    private val leftPunchTargets = mutableSetOf<Enemy>()
    private val rightPunchTargets = mutableSetOf<Enemy>()
    private val leftPunchFixture = body.createFixture(
        FixtureDef(
            shape = PolygonShape().apply {
                setAsBox(punchWidth, punchDepth, center = tempVec2.set(-punchDistance, 0f), angle = 0f.radians)
            },
            filter = Filter().apply {
                categoryBits = ContactBits.REI_PUNCH
                maskBits = ContactBits.ENEMY
            },
            userData = leftPunchTargets,
            isSensor = true
        )
    ) ?: error("Fixture is null! Should not happen!")
    private val rightPunchFixture = body.createFixture(
        FixtureDef(
            shape = PolygonShape().apply {
                setAsBox(punchWidth, punchDepth, center = tempVec2.set(punchDistance, 0f), angle = 0f.radians)
            },
            filter = Filter().apply {
                categoryBits = ContactBits.REI_PUNCH
                maskBits = ContactBits.ENEMY
            },
            userData = rightPunchTargets,
            isSensor = true
        )
    ) ?: error("Fixture is null! Should not happen!")

    override val context: Map<Any, Body> = mapOf(Body::class to body)

    private val zeroVec2 = Vec2()
    private val tempColor = MutableColor()

    private var isFacingLeft = false
    private var wasPunching = false
    private var nextLeftPunch = true
    private var punchCooldown = 0f

    var movingToBeatUnlocked = true
    var movingToBeat = false
    private var lastBeatPosition = 0f
    private var dashCooldown = 0f

    private var activatePunch = false
    fun activatePunch() {
        activatePunch = true
    }

    override fun update(dt: Duration, millis: Float, toBeat: Float) {
        val xMovement = controller.axis(GameInput.HORIZONTAL)
        val yMovement = controller.axis(GameInput.VERTICAL)
        val anyKeyDown = controller.down(GameInput.ANY)
        val matchBeat = toBeat >= 0.0f && toBeat <= 0.3f || toBeat > 0.9f
        if (toBeat < lastBeatPosition && !anyKeyDown && xMovement == 0f && yMovement == 0f) {
            println("beat reset!")
            movingToBeat = false
            movingToBeatUnlocked = true
        }
        lastBeatPosition = toBeat

        if (punchCooldown > 0f) {
            body.linearVelocity.set(0f, 0f)
            punchCooldown -= millis
            if (punchCooldown > 0f) {
                currentAnimation.update(dt)
                return
            }
        }
        if (dashCooldown > 0f) {
            dashCooldown -= millis
            if (dashCooldown > 0f) {
                return
            }
            body.linearVelocity.set(0f, 0f)
        }


        if (anyKeyDown || xMovement != 0f || yMovement != 0f) {
            lastBeatPosition = toBeat
            if (movingToBeatUnlocked && !matchBeat) {
                println("not moving to beat! $toBeat")
                movingToBeat = false
                movingToBeatUnlocked = false
            }
        }

        if (xMovement != 0f || yMovement != 0f) {
            wasPunching = false
            nextLeftPunch = true
            currentAnimation = animations.walk
            if (isFacingLeft && xMovement > 0f) {
                isFacingLeft = false
            } else if (!isFacingLeft && xMovement < 0f) {
                isFacingLeft = true
            }

            if (movingToBeatUnlocked) {
                println("dash to beat!")
                movingToBeat = true
                dashCooldown = 200f
                body.linearVelocity.set(xMovement * 2.5f, yMovement * 2.5f)
            }
            body.isAwake = true
        } else if (!wasPunching) {
            currentAnimation = animations.idle
        }

        if (dashCooldown <= 0f) {
            // it will also stop the character if no movement is requested
            body.linearVelocity.set(xMovement, yMovement)
        }

        if (controller.pressed(GameInput.ATTACK) || controller.pressed(GameInput.JUMP) || controller.justTouched) {
            punchCooldown = 300f//if (wasPunching) 600f else 900f
            currentAnimation = if (nextLeftPunch) {
                animations.leftPunch
            } else {
                animations.rightPunch
            }
            wasPunching = true
            nextLeftPunch = !nextLeftPunch
            activateParticles()
        }

        currentAnimation.update(dt)

        if (activatePunch) {
            activatePunch = false
            movingToBeat = movingToBeatUnlocked && matchBeat
            if (isFacingLeft) {
                leftPunchTargets.forEach {
                    println("left punch $it")
                    it.hit(body.position, movingToBeat)
                }
            } else {
                rightPunchTargets.forEach {
                    println("right punch $it")
                    it.hit(body.position, movingToBeat)
                }
            }
        }
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
            val width = slice.width
            val height = slice.height
            val textureData = slice.texture.textureData
            if (textureData is PixmapTextureData) {
                val xOffset = texturePositionX(width.toFloat())
                val yOffset = texturePositionY(height.toFloat())
                val midHeight = height / 2
                var firstMeaningfulX = 0
                val endX = slice.x + width
                val endY = slice.y + height
                for (sliceX in slice.x until endX step pixelWidthInt) {
                    for (sliceY in slice.y until endY step pixelHeightInt) {
                        val x = sliceX - slice.x
                        val y = sliceY - slice.y
                        val pixelColor = textureData.pixmap.get(sliceX, sliceY)
                        if (pixelColor != 0) {
                            if (firstMeaningfulX == 0) {
                                firstMeaningfulX = x
                            }
                            particleSimulator.alloc(
                                Textures.white,
                                xOffset + width * 2 - x / Game.PPU,
                                yOffset + y / Game.PPU
                            )
                                .apply {
                                    scale(Game.IPPU)
                                    delay =
                                        ((width - x) / 7.5f + 1f + (-0.2f..0.2f).random()).seconds
                                    color.setRgba8888(pixelColor)
                                    xDelta = (0.4f + (-0.25f..0.25f).random()) / Game.PPU
                                    yDelta =
                                        (midHeight - y) / 500f / Game.PPU + (-0.45f..0.45f).random() / Game.PPU
                                    life = 4f.seconds
                                    friction = 1.03f
                                    alphaDelta = -0.005f
                                }
                        }
                    }
                }
            }
        }
    }

    override fun render(batch: Batch) {
        currentAnimation.currentKeyFrame?.let { frame ->
            val width = frame.width / Game.PPU
            val height = frame.height / Game.PPU
            val positionX = texturePositionX(width)
            val positionY = texturePositionY(height)
            //println("player at $positionX, $positionY")
            batch.draw(
                frame,
                positionX,
                positionY,
                width = width,
                height = height,
                flipX = isFacingLeft
            )
        }
    }

    private fun texturePositionX(width: Float) = body.position.x - physicalHw - width / 2f
    private fun texturePositionY(height: Float) = body.position.y - physicalHh - height
}

private fun MutableColor.setArgb888(argb8888: Int): MutableColor {
    a = ((argb8888 and 0xff000000.toInt()) ushr 24) / 255f
    b = ((argb8888 and 0x00ff0000) ushr 16) / 255f
    g = ((argb8888 and 0x0000ff00) ushr 8) / 255f//?
    r = (argb8888 and 0x000000ff) / 255f //?
    return this
}
