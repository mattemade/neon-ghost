package io.itch.mattemade.neonghost.character.rei

import com.littlekt.file.Vfs
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.ParticleSimulator
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.graphics.gl.PixmapTextureData
import com.littlekt.graphics.toFloatBits
import com.littlekt.input.InputMapController
import com.littlekt.math.MutableVec2f
import com.littlekt.math.random
import com.littlekt.util.seconds
import com.soywiz.korma.geom.Angle
import com.soywiz.korma.geom.radians
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.character.DepthBasedRenderable
import io.itch.mattemade.neonghost.character.enemy.Enemy
import io.itch.mattemade.neonghost.pixelPerfectPosition
import io.itch.mattemade.neonghost.world.ContactBits
import io.itch.mattemade.utils.animation.SignallingAnimationPlayer
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.Filter
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration

class Player(
    initialPosition: Vec2,
    private val world: World,
    private val assets: Assets,
    private val controller: InputMapController<GameInput>,
    private val particleSimulator: ParticleSimulator,
    private val vfs: Vfs,
    val initialHealth: Int,
    var isMagicGirl: Boolean = false,
    private val canAct: () -> Boolean,
    val gameOver: () -> Unit,
    private val changePlaybackRate: (Float) -> Unit,
) : Releasing by Self(),
    DepthBasedRenderable {

    //private val textureSizeInWorldUnits = Vec2(60f / Game.PPU, 96f / Game.PPU)
    val pixelWidth = 1f//textureSizeInWorldUnits.x
    val pixelWidthInt = pixelWidth.toInt()
    val pixelHeight = 1f//textureSizeInWorldUnits.y
    val pixelHeightInt = pixelHeight.toInt()
    private val physicalHw = 1f / Game.PPU
    private val physicalHh = 1f / Game.PPU
    private val punchDistance = 28f / Game.PPU
    private val punchWidth = 16f / Game.PPU
    private val punchDepth = 10f / Game.PPU
    var health: Int = initialHealth
        private set


    private val body = world.createBody(
        BodyDef(
            type = BodyType.DYNAMIC,
            userData = this,
        ).apply {
            position.set(initialPosition)
        }
    ).rememberTo { world.destroyBody(it) }

    val x get() = body.position.x
    val y get() = body.position.y
    override val depth: Float get() = y

    private var animations = assets.animation.normalReiAnimations

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
                maskBits =
                    ContactBits.WALL or ContactBits.CAMERA or ContactBits.TRIGGER or ContactBits.ENEMY_PUNCH
            },
            userData = this
        )
    ) ?: error("Fixture is null! Should not happen!")

    private val tempVec2 = Vec2()
    private val tempVec2f = MutableVec2f(0f)
    private val tempVec2f2 = MutableVec2f(0f)

    private val leftPunchTargets = mutableSetOf<Enemy>()
    private val rightPunchTargets = mutableSetOf<Enemy>()
    private val leftPunchFixture = body.createFixture(
        FixtureDef(
            shape = PolygonShape().apply {
                setAsBox(
                    punchWidth / 2f,
                    punchDepth / 2f,
                    center = tempVec2.set(-punchDistance, 0f),
                    angle = 0f.radians
                )
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
                setAsBox(
                    punchWidth / 2f,
                    punchDepth / 2f,
                    center = tempVec2.set(punchDistance, 0f),
                    angle = 0f.radians
                )
            },
            filter = Filter().apply {
                categoryBits = ContactBits.REI_PUNCH
                maskBits = ContactBits.ENEMY
            },
            userData = rightPunchTargets,
            isSensor = true
        )
    ) ?: error("Fixture is null! Should not happen!")

    private val zeroVec2 = Vec2()
    private val tempColor = MutableColor()

    private var isFacingLeft = false
    private var wasPunching = false
    private var nextLeftPunch = true
    private var punchCooldown = 0f

    var movingToBeat = false
    var movingOffBeat = false
    private var lastBeatPosition = 0f
    private var dashCooldown = 0f
    private var hitCooldown = 0f

    private var activatePunch = false
    fun activatePunch() {
        activatePunch = true
    }

    private var keepMoving = false

    //private var castingTimes = 0
    private var castingTime = 0f
    private var reducingTime = 0f

    fun hit(from: Vec2, difficulty: Float) {
        if (health == 0) {
            return
        }
        health = max(0, health - difficulty.toInt())
        if (castingTime > 0f) {
            if (castingTime / castTime > castBeforeSlowingTime) {
                changePlaybackRate(1f)
            }
            castingTime = 0f
        }
        hitCooldown = 300f
        currentAnimation = animations.hit
        val direction = tempVec2f.set(body.position.x, body.position.y).subtract(from.x, from.y)
        val force = if (health == 0) 3f else 3f * difficulty
        tempVec2f2.set(force, 0f)
        val rotation = direction.angleTo(tempVec2f2)
        if (health > 0) {
            val distance = direction.length()
            tempVec2f2.scale(1f / distance)
            tempVec2f2.x = min(2f, tempVec2f2.x)
        }
        tempVec2f2.rotate(rotation)
        body.linearVelocity.set(tempVec2f2.x, tempVec2f2.y)
        body.isAwake = true
    }

    fun stopBody(resetAnimationToIdle: Boolean = false) {
        if (resetAnimationToIdle) {
            currentAnimation = animations.idle
        }
        body.linearVelocity.set(0f, 0f)
        tempVec2.set(body.position)
        body.setTransform(tempVec2, Angle.ZERO)
    }

    override fun update(
        dt: Duration,
        millis: Float,
        notAdjustedDt: Duration,
        toBeat: Float,
        toMeasure: Float
    ) {
        if (!canAct()) {
            stopBody()
            currentAnimation.update(dt)
            return
        }
        val xMovement = controller.axis(GameInput.HORIZONTAL)
        val yMovement = controller.axis(GameInput.VERTICAL)
        val moving = xMovement != 0f || yMovement != 0f
        val anyAction = controller.down(GameInput.ANY_ACTION)
        val matchBeat = toBeat < 0.2f || toBeat > 0.9f || toMeasure < 0.075f || toMeasure > 0.9625f

        if (toBeat < lastBeatPosition && movingOffBeat) {
            movingToBeat = false
            movingOffBeat = false
        }
        lastBeatPosition = toBeat

        if (hitCooldown > 0f) {
            punchCooldown = 0f
            dashCooldown = 0f
            hitCooldown -= millis
            if (hitCooldown > 0f) {
                currentAnimation.update(dt)
                return
            }
        }
        if (health == 0) {
            gameOver()
            return
        }
        if (punchCooldown > 0f) {
            if (body.linearVelocity.x != 0f || body.linearVelocity.y != 0f) {
                stopBody()
            }
            punchCooldown -= millis
            if (punchCooldown > 0f) {
                currentAnimation.update(dt)
                return
            }
            movingToBeat = false
        }
        if (dashCooldown > 0f) {
            dashCooldown -= millis
            if (dashCooldown > 0f) {
                return
            }
            movingToBeat = false
        }

        if (moving) {
            wasPunching = false
            nextLeftPunch = true
            currentAnimation = animations.walk
            if (isFacingLeft && xMovement > 0f) {
                isFacingLeft = false
            } else if (!isFacingLeft && xMovement < 0f) {
                isFacingLeft = true
            }


            if (matchBeat && !keepMoving) {
                movingToBeat = true
                dashCooldown = 200f
                body.linearVelocity.set(xMovement * 2.5f, yMovement * 2.5f)
            } else {
                if (keepMoving) {
                    // was moving before, and continuing to move off-beat
                    if (movingToBeat) {
                        movingToBeat = false
                    }
                } else {
                    // just started moving, but not dashing - so it was off-beat
                    movingOffBeat = true
                    movingToBeat = false
                }
                body.linearVelocity.set(xMovement, yMovement)
            }
            body.isAwake = true
            keepMoving = true
        } else {
            if (!wasPunching) {
                currentAnimation = animations.idle
            }
            keepMoving = false
            stopBody()
        }

        if (isMagicGirl) {
            val notAdjustedSeconds = notAdjustedDt.seconds
            if (controller.down(GameInput.MAGIC)) {
                dashCooldown = 0f
                punchCooldown = 0f
                movingOffBeat = false
                movingToBeat = false
                if (controller.pressed(GameInput.ATTACK)) {
                    if (castingTime / castTime >= 2) {
                        // TODO attack by shadow
                        println("cast projectile from shadow")
                    } else {
                        val castPower = castingTime / castTime
                        // TODO: cast projectile
                        println("cast projectile $castPower")
                        // and then if will automatically fallback into punching!
                    }
                    changePlaybackRate(1f)
                    castingTime = 0f
                } else { // just holding the Magic button
                    if (xMovement != 0f || yMovement != 0f) {
                        // moving while holding magic - it will drain focus
                        castingTime = max(0f, castingTime - notAdjustedSeconds)
                        recalculatePlaybackRate()
                    } else {
                        stopBody()
                        currentAnimation = animations.prepare
                        if (castingTime < castTime * castsToStopTime) {
                            castingTime += notAdjustedSeconds
                            if (castingTime / castTime >= castsToStopTime) {
                                // TODO: activate shadow ghost
                                changePlaybackRate(0.000000000001f)
                                println("shadow ghost is activated")
                            } else {
                                recalculatePlaybackRate()
                            }
                        } else { // castingTime == 2
                            // TODO: move shadow ghost
                            //println("shadow ghost is moving")
                        }
                    }
                }
            } else if (castingTime > 0f) { // releasing after casting
                if (castingTime / castTime >= castsToStopTime) {
                    // TODO aoe by shadow
                    println("aoe by shadow")
                } else {
                    val castPower = castingTime / castTime
                    // TODO: cast aoe
                    println("aoe by Rei $castPower")
                }
                changePlaybackRate(1f)
                castingTime = 0f
            }
        }

        if (controller.pressed(GameInput.ATTACK) || (!isMagicGirl && controller.pressed(GameInput.MAGIC))) {
            punchCooldown = 300f//if (wasPunching) 600f else 900f
            stopBody()
            currentAnimation = if (nextLeftPunch) {
                animations.leftPunch
            } else {
                animations.rightPunch
            }
            wasPunching = true
            nextLeftPunch = !nextLeftPunch
            //activateParticles()
        }

        currentAnimation.update(dt) // will trigger animation callbacks

        if (activatePunch) {
            activatePunch = false
            movingToBeat = matchBeat
            movingOffBeat = !matchBeat
            if (isFacingLeft) {
                leftPunchTargets.forEach {
                    it.hit(body.position, if (movingToBeat) 2 else 1)
                }
            } else {
                rightPunchTargets.forEach {
                    it.hit(body.position, if (movingToBeat) 2 else 1)
                }
            }
        }
    }

    private fun recalculatePlaybackRate() {
        if (castingTime / castTime >= castBeforeSlowingTime) {
            val castingRemainsDuringSlowingPeriod =
                castTime * castsToStopTime - castingTime
            val slowingPeriod =
                castTime * (castsToStopTime - castBeforeSlowingTime)
            val playbackRate = castingRemainsDuringSlowingPeriod / slowingPeriod
            changePlaybackRate(max(0.000000000001f, playbackRate))
        } else {
            changePlaybackRate(1f)
        }
    }

    private fun activateParticles() {
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
                                assets.texture.white,
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


    fun transform() {
        isMagicGirl = true
        animations = assets.animation.magicalReiAnimations
    }

    private var shaperRenderer: ShapeRenderer? = null


    override fun render(batch: Batch) {
        currentAnimation.currentKeyFrame?.let { frame ->
            val width = frame.width / Game.PPU
            val height = frame.height / Game.PPU
            val xOffset = (frame.width * 0.1f / Game.PPU).pixelPerfectPosition
            val yOffset = (3f / Game.PPU).pixelPerfectPosition
            val positionX =
                texturePositionX(width).pixelPerfectPosition + if (isFacingLeft) -xOffset else xOffset
            val positionY = texturePositionY(height).pixelPerfectPosition + yOffset
            batch.draw(
                frame,
                positionX,
                positionY,
                width = width,
                height = height,
                flipX = isFacingLeft
            )
            /*if (shaperRenderer == null) {
                shaperRenderer = ShapeRenderer(batch)
            }

            shaperRenderer?.filledRectangle(
                x = body.position.x - punchDistance - punchWidth / 2f,
                y = body.position.y - punchDepth / 2f,
                width = punchWidth,
                height = punchDepth,
                color = (if (isFacingLeft) Color.RED else Color.YELLOW).toFloatBits(),
            )
            shaperRenderer?.filledRectangle(
                x = body.position.x + punchDistance - punchWidth / 2f,
                y = body.position.y - punchDepth / 2f,
                width = punchWidth,
                height = punchDepth,
                color = (if (!isFacingLeft) Color.RED else Color.YELLOW).toFloatBits(),
            )

            shaperRenderer?.filledRectangle(
                x = body.position.x - physicalHw / 2f,
                y = body.position.y - physicalHh / 2f,
                width = physicalHw,
                height = physicalHh,
                color = Color.BLUE.toFloatBits(),
            )*/
        }
    }


    override fun renderShadow(shapeRenderer: ShapeRenderer) {
        currentAnimation.currentKeyFrame?.let { frame ->
            val width = frame.width / Game.PPU
            val minRx = width / 4f
            val minRy = width / 8f
            val deltaRx = spellRx - minRx
            val deltaRy = spellRy - minRy
            val castingTimes = (castingTime / castTime).toInt()
            val ratio = if (castingTimes > 0) 1f else castingTime / castTime

            shapeRenderer.filledEllipse(
                x = body.position.x.pixelPerfectPosition,
                y = body.position.y.pixelPerfectPosition,
                rx = minRx + deltaRx * ratio,
                ry = minRy + deltaRy * ratio,
                innerColor = if (!isMagicGirl) Game.shadowColor else magicGirlShadowColor,
                outerColor = if (!isMagicGirl) Game.shadowColor else magicGirlShadowColor,
            )
            for (i in 0 until castingTimes) {
                val doubleRatio = if (castingTimes > i + 1) 1f else (castingTime % castTime) / castTime
                shapeRenderer.filledEllipse(
                    x = body.position.x.pixelPerfectPosition,
                    y = body.position.y.pixelPerfectPosition,
                    rx = spellRx * doubleRatio,
                    ry = spellRy * doubleRatio,
                    innerColor = magicGirlShadowColor,
                    outerColor = magicGirlShadowColor,
                )
            }

            if (castingTime > 0f || castingTimes > 0) {
                shapeRenderer.ellipse(
                    x = body.position.x.pixelPerfectPosition,
                    y = body.position.y.pixelPerfectPosition,
                    rx = spellRx,
                    ry = spellRy,
                    thickness = Game.IPPU,
                    color = magicGirlShadowEdgeColor
                )
            }

        }
    }

    private fun texturePositionX(width: Float) = body.position.x - width / 2f
    private fun texturePositionY(height: Float) = body.position.y - height

    companion object {
        private val magicGirlShadowEdgeColor = MutableColor(0.325f, 0.212f, 0.384f, 1f).toFloatBits()
        private val magicGirlShadowColor = MutableColor(0.325f, 0.212f, 0.384f, 0.5f).toFloatBits()
        private val castTime = 2f
        private val reduceTime = 0.1f
        private val spellRx = 64f * Game.IPPU
        private val spellRy = 32f * Game.IPPU
        private const val castBeforeSlowingTime = 1
        private const val castsToStopTime = 2
    }

}

private fun MutableColor.setArgb888(argb8888: Int): MutableColor {
    a = ((argb8888 and 0xff000000.toInt()) ushr 24) / 255f
    b = ((argb8888 and 0x00ff0000) ushr 16) / 255f
    g = ((argb8888 and 0x0000ff00) ushr 8) / 255f//?
    r = (argb8888 and 0x000000ff) / 255f //?
    return this
}
