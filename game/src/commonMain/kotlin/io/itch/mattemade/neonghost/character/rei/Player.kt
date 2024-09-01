package io.itch.mattemade.neonghost.character.rei

import com.littlekt.file.Vfs
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.ParticleSimulator
import com.littlekt.graphics.g2d.shape.JoinType
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.graphics.gl.PixmapTextureData
import com.littlekt.graphics.toFloatBits
import com.littlekt.input.InputMapController
import com.littlekt.math.MutableVec2f
import com.littlekt.math.PI2
import com.littlekt.math.geom.radians
import com.littlekt.math.random
import com.littlekt.util.milliseconds
import com.littlekt.util.seconds
import com.soywiz.korma.geom.Angle
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.character.DepthBasedRenderable
import io.itch.mattemade.neonghost.character.enemy.Enemy
import io.itch.mattemade.neonghost.pixelPerfectPosition
import io.itch.mattemade.neonghost.tempo.Choreographer
import io.itch.mattemade.neonghost.world.ContactBits
import io.itch.mattemade.utils.animation.SignallingAnimationPlayer
import io.itch.mattemade.utils.math.fill
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
import kotlin.random.Random
import kotlin.time.Duration
import com.soywiz.korma.geom.radians as boxRadians

class Player(
    initialPosition: Vec2,
    private val world: World,
    private val choreographer: Choreographer,
    private val assets: Assets,
    private val controller: InputMapController<GameInput>,
    private val particleSimulator: ParticleSimulator,
    private val vfs: Vfs,
    val initialHealth: Int,
    var isMagicGirl: Boolean = false,
    private val canAct: () -> Boolean,
    val gameOver: () -> Unit,
    private val changePlaybackRateExternal: (Float) -> Unit,
    private val spawnNeonGhost: (facingLeft: Boolean) -> Unit,
    private val castAoe: (Vec2, Int) -> Unit,
    private val castProjectile: (Vec2, Int, Boolean) -> Unit,
    private val spawnParticles: (
        depth: Float,
        instances: Int,
        lifeTime: Float,
        fillData: (
            index: Int,
            startColor: FloatArray,
            endColor: FloatArray,
            startPosition: FloatArray,
            endPosition: FloatArray,
            activeBetween: FloatArray
        ) -> Unit,
    ) -> Unit
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

    private var animations =
        if (isMagicGirl) assets.animation.magicalReiAnimations else assets.animation.normalReiAnimations

    internal var currentAnimation: SignallingAnimationPlayer = animations.idle
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
                    angle = 0f.boxRadians
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
                    angle = 0f.boxRadians
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

    var isFacingLeft = false
    private var wasPunching = false
    private var nextLeftPunch = true
    private var punchCooldown = 0f
    private var spellCooldown = 0f
    private var focusCooldown = 0f

    var movingToBeat = false
    var movingOffBeat = false
    private var lastBeatPosition = 0f
    private var dashCooldown = 0f
    private var hitCooldown = 0f
    private val maxHitCooldown = 300f
    private val deathCooldown = 1000f
    private val hitSlomoUntil = 250f
    private val minHitSlomoRate = 0.1f

    private var activatePunch = false
    fun activatePunch() {
        activatePunch = true
    }

    private var keepMoving = false

    //private var castingTimes = 0
    private var castingTime = 0f
    private var reducingTime = 0f
    private var castingSound = -1

    fun changePlaybackRate(value: Float) {
        changePlaybackRateExternal(value)
        if (castingSound != -1) {
            assets.sound.powerUpLoop.sound.setPlaybackRate(castingSound, value)
        }
    }

    fun hit(from: Vec2, difficulty: Float) {
        if (health <= 0) {
            return
        }
        health = max(0, health - difficulty.toInt())
        choreographer.sound(assets.sound.damage.sound, body.position.x, body.position.y)
        if (castingTime > 0f) {
            if (castingTime / castTime > castBeforeSlowingTime) {
                updateReducingTime()
            }
            castingTime = 0f
        }
        focusCooldown = 0f
        hitCooldown = if (health <= 0) deathCooldown else maxHitCooldown
        currentAnimation = animations.hit
        val direction = tempVec2f.set(body.position.x, body.position.y).subtract(from.x, from.y)
        val force = if (health <= 0) 3f else 3f * difficulty
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

    private fun updateReducingTime() {
        if (castingTime / castTime <= castBeforeSlowingTime) {
            reducingTime = 0f
            return
        }
        val castWithinSlowingPeriod = castingTime - castTime * castBeforeSlowingTime
        val slowingPeriod =
            castTime * (castsToStopTime - castBeforeSlowingTime)
        val rate = castWithinSlowingPeriod / slowingPeriod
        reducingTime = reduceTime * rate
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
        toMeasure: Float,
        isFighting: Boolean
    ) {
        if (reducingTime > 0f) {
            reducingTime -= notAdjustedDt.seconds
            if (reducingTime <= 0f) {
                changePlaybackRate(1f)
            } else {
                changePlaybackRate(1f - reducingTime / reduceTime)
            }
        }
        if (spellCooldown > 0f) {
            spellCooldown = max(0f, spellCooldown - dt.seconds)
        }
        if (!canAct()) {
            stopBody()
            currentAnimation.update(dt)
            return
        }
        val xMovement = controller.axis(GameInput.HORIZONTAL)
        val yMovement = controller.axis(GameInput.VERTICAL)
        val moving =
            (xMovement != 0f || yMovement != 0f) && castingTime < castTime * castsToStopTime
        val matchBeat = toBeat < 0.2f || toBeat > 0.9f || toMeasure < 0.075f || toMeasure > 0.9625f

        if (toBeat < lastBeatPosition && movingOffBeat) {
            movingToBeat = false
            movingOffBeat = false
        }
        lastBeatPosition = toBeat

        if (hitCooldown > 0f) {
            punchCooldown = 0f
            dashCooldown = 0f
            hitCooldown -= if (health <= 0) notAdjustedDt.milliseconds else millis
            if (hitCooldown > 0f) {
                val rate = if (health <= 0) {
                    hitCooldown / deathCooldown
                } else {
                    minHitSlomoRate + (1f - minHitSlomoRate) * if (hitCooldown > hitSlomoUntil) {
                        (hitCooldown - hitSlomoUntil) / (maxHitCooldown - hitSlomoUntil)
                    } else {
                        (hitSlomoUntil - hitCooldown) / hitSlomoUntil
                    }
                }
                changePlaybackRate(rate)
                currentAnimation.update(dt)
                return
            }
            changePlaybackRate(if (health <= 0) 0f else 1f)
        }
        if (health <= 0) {
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

        var speed = 1f
        if (moving) {
            wasPunching = false
            nextLeftPunch = true
            currentAnimation = animations.walk
            if (isFacingLeft && xMovement > 0f) {
                isFacingLeft = false
            } else if (!isFacingLeft && xMovement < 0f) {
                isFacingLeft = true
            }


            if (matchBeat && !keepMoving && isFighting) {
                movingToBeat = true
                dashCooldown = 200f
                body.linearVelocity.set(xMovement * 2.5f, yMovement * 2.5f)
                choreographer.sound(assets.sound.dash.sound, body.position.x, body.position.y)
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
                if (isFighting) {
                    body.linearVelocity.set(xMovement, yMovement)
                    speed = body.linearVelocity.length()
                } else {
                    body.linearVelocity.set(xMovement, yMovement).mulLocal(1.5f)
                    speed = body.linearVelocity.length()// / 2f
                }
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
                focusCooldown = min(maxFocusCooldown, focusCooldown + dt.seconds)
                if (spellCooldown == 0f && focusCooldown == maxFocusCooldown) {
                    dashCooldown = 0f
                    punchCooldown = 0f
                    movingOffBeat = false
                    movingToBeat = false
                    if (controller.pressed(GameInput.ATTACK)) {
                        if (castingTime / castTime < castsToStopTime) {
                            val castPower = castingTime / castTime
                            castProjectile(body.position, castPower.toInt(), isFacingLeft)
                            // and then if will automatically fallback into punching!
                        }
                        updateReducingTime()
                        castingTime = 0f
                        focusCooldown = 0f
                    } else { // just holding the Magic button
                        if (moving) {
                            if (castingSound != -1) {
                                assets.sound.powerUpLoop.sound.stop(castingSound)
                                castingSound = -1
                            }
                            // moving while holding magic - it will drain focus
                            castingTime = max(0f, castingTime - notAdjustedSeconds / 2f)
                            recalculatePlaybackRate()
                        } else {
                            if (castingSound != -1) {
                                assets.sound.powerUpLoop.sound.setPosition(castingSound, x, y)
                            } else {
                                castingSound = choreographer.sound(
                                    assets.sound.powerUpLoop.sound,
                                    x,
                                    y,
                                    looping = true
                                )
                            }
                            stopBody()
                            currentAnimation = animations.prepare
                            if (castingTime < castTime * castsToStopTime) {
                                castingTime += notAdjustedSeconds
                                if (castingTime / castTime >= castsToStopTime) {
                                    if (castingSound != -1) {
                                        assets.sound.powerUpLoop.sound.stop(castingSound)
                                        castingSound = -1
                                    }
                                    choreographer.soundIgnoringPlaybackRate(assets.sound.slowMo.sound, x, y)
                                    spawnNeonGhost(isFacingLeft)
                                    changePlaybackRate(0.000000000001f)
                                } else {
                                    recalculatePlaybackRate()
                                }
                            }
                        }
                    }
                }
            } else if (castingTime > 0f) { // releasing after casting
                if (castingSound != -1) {
                    assets.sound.powerUpLoop.sound.stop(castingSound)
                    castingSound = -1
                }
                if (castingTime / castTime < castsToStopTime) {
                    val castPower = (castingTime / castTime).toInt()
                    castAoe(body.position, castPower)
                    // TODO: maybe return this?
                    //spellCooldown = castingTime
                }
                updateReducingTime()
                castingTime = 0f
                focusCooldown = 0f
            } else {
                if (castingSound != -1) {
                    assets.sound.powerUpLoop.sound.stop(castingSound)
                    castingSound = -1
                }
                focusCooldown = max(0f, focusCooldown - dt.seconds)
            }
        }

        if (isFighting || reducingTime > 0f) {
            if (controller.pressed(GameInput.ATTACK)) {
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
        }

        currentAnimation.update(dt * speed.toDouble()) // will trigger animation callbacks

        if (activatePunch) {
            activatePunch = false
            movingToBeat = matchBeat
            movingOffBeat = !matchBeat

            choreographer.sound(
                if (movingToBeat) assets.sound.powerWhoosh.sound else assets.sound.whoosh.sound,
                x,
                y
            )

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
        val currentKeyFrame = currentAnimation.currentKeyFrame
        if (currentKeyFrame == null) {
            currentAnimation.update(Duration.ZERO)
        }
        currentAnimation.currentKeyFrame?.let { slice ->
            val width = slice.width
            val height = slice.height

            val textureData = slice.texture.textureData
            if (textureData is PixmapTextureData) {
                val xOffset = texturePositionX(width / Game.PPU) * 2f
                val yOffset = texturePositionY(height / Game.PPU) * 2f

                spawnParticles(
                    depth,
                    width * height * 4,
                    20000f
                ) { index, startColor, endColor, startPosition, endPosition, activeBetween ->
                    val x = (index / 4) % width
                    val y = (index / 4) / width
                    val pixelColor = textureData.pixmap.get(slice.x + x, slice.y + y)
                    if (pixelColor == 0) {
                        startColor.fill(0f)
                        endColor.fill(0f)
                        startPosition.fill(0f)
                        endPosition.fill(0f)
                        activeBetween.fill(0f)
                        return@spawnParticles
                    }
                    tempColor.setRgba8888(pixelColor)
                    startColor.fill(tempColor.r, tempColor.g, tempColor.b, tempColor.a)
                    endColor.fill(tempColor.r, tempColor.g, tempColor.b, 0f)
                    startPosition.fill(
                        xOffset + x * 2 / Game.PPU,
                        yOffset + y * 2 / Game.PPU
                    )//xOffset + width * 2 - x / Game.PPU, yOffset + y / Game.PPU)
                    endPosition.fill(
                        startPosition[0] - Random.nextFloat() * 4f * width * Game.IPPU,
                        startPosition[1] + (-2f + Random.nextFloat() * 4f) * height * Game.IPPU
                    )
                    activeBetween[0] = ((width - x) / 17.5f + 1f + (-0.2f..0.2f).random()) * 1000f
                    activeBetween[1] = activeBetween[0] + 4000f
                }
            }
        }
    }


    fun transform() {
        isMagicGirl = true
        animations = assets.animation.magicalReiAnimations
    }

    private var shaperRenderer: ShapeRenderer? = null


    private var ghostXOffset = 0f
    private var ghostYOffset = 0f
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
            val focusedMagic = if (castingTime > 0f) castingTime else spellCooldown
            val castingTimes = (focusedMagic / castTime).toInt()
            if (castingTimes == castsToStopTime) {
                shapeRenderer.filledEllipse(
                    x = body.position.x.pixelPerfectPosition,
                    y = body.position.y.pixelPerfectPosition,
                    rx = minRx,
                    ry = minRy,
                    innerColor = magicGirlShadowColor,
                    outerColor = magicGirlShadowColor,
                )
            } else {
                val ratio = if (castingTimes > 0) 1f else focusedMagic / castTime

                shapeRenderer.filledEllipse(
                    x = body.position.x.pixelPerfectPosition,
                    y = body.position.y.pixelPerfectPosition,
                    rx = minRx + deltaRx * ratio,
                    ry = minRy + deltaRy * ratio,
                    innerColor = if (!isMagicGirl) Game.shadowColor else magicGirlShadowColor,
                    outerColor = if (!isMagicGirl) Game.shadowColor else magicGirlShadowColor,
                )
                for (i in 0 until castingTimes) {
                    val doubleRatio =
                        if (castingTimes > i + 1) 1f else (focusedMagic % castTime) / castTime
                    shapeRenderer.filledEllipse(
                        x = body.position.x.pixelPerfectPosition,
                        y = body.position.y.pixelPerfectPosition,
                        rx = spellRx * doubleRatio,
                        ry = spellRy * doubleRatio,
                        innerColor = magicGirlShadowColor,
                        outerColor = magicGirlShadowColor,
                    )
                }

                if (focusCooldown > 0f) {
                    val startAngle = (0.0).radians
                    val angle = focusCooldown / maxFocusCooldown * PI2
                    shapeRenderer.ellipse(
                        x = body.position.x.pixelPerfectPosition,
                        y = body.position.y.pixelPerfectPosition,
                        rx = spellRx,
                        ry = spellRy,
                        thickness = Game.IPPU,
                        color = magicGirlShadowEdgeColor,
                        startAngle = startAngle,
                        joinType = JoinType.NONE,
                        radians = angle.toFloat(),
                    )
                }
            }
        }
    }

    private fun texturePositionX(width: Float) = body.position.x - width / 2f
    private fun texturePositionY(height: Float) = body.position.y - height

    companion object {
        val magicGirlShadowEdgeColor = MutableColor(0.325f, 0.212f, 0.384f, 1f).toFloatBits()
        val magicGirlShadowColor = MutableColor(0.325f, 0.212f, 0.384f, 0.5f).toFloatBits()
        private val castTime = 1.5f
        private val reduceTime = 2f
        private val maxFocusCooldown = 0.5f
        val spellRx = 64f * Game.IPPU
        val spellRy = 32f * Game.IPPU
        private const val castBeforeSlowingTime = 1
        const val castsToStopTime = 2
        const val maxPlayerHealth = 10
    }

}

private fun MutableColor.setArgb888(argb8888: Int): MutableColor {
    a = ((argb8888 and 0xff000000.toInt()) ushr 24) / 255f
    b = ((argb8888 and 0x00ff0000) ushr 16) / 255f
    g = ((argb8888 and 0x0000ff00) ushr 8) / 255f//?
    r = (argb8888 and 0x000000ff) / 255f //?
    return this
}
