package io.itch.mattemade.neonghost.character.enemy

import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.CharacterAnimations
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.character.DepthBasedRenderable
import io.itch.mattemade.neonghost.character.rei.Player
import io.itch.mattemade.neonghost.world.ContactBits
import com.littlekt.file.Vfs
import com.littlekt.graphics.Color
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.ParticleSimulator
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.graphics.toFloatBits
import com.littlekt.input.InputMapController
import com.littlekt.math.MutableVec2f
import com.soywiz.korma.geom.Angle
import com.soywiz.korma.geom.radians
import io.itch.mattemade.blackcat.input.GameInput
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
import kotlin.math.min
import kotlin.time.Duration

class Enemy(
    initialPosition: Vec2,
    private val player: Player,
    private val world: World,
    private val assets: Assets,
    animations: CharacterAnimations,
    private val controller: InputMapController<GameInput>,
    private val particleSimulator: ParticleSimulator,
    private val vfs: Vfs,
    private val difficulty: Float = 1f,
    val initialHeath: Int = 3,
    private val canAct: () -> Boolean,
    private val onDeath: (Enemy) -> Unit,
) : Releasing by Self(),
    DepthBasedRenderable {

    val pixelWidth = 1f//textureSizeInWorldUnits.x
    val pixelWidthInt = pixelWidth.toInt()
    val pixelHeight = 1f//textureSizeInWorldUnits.y
    val pixelHeightInt = pixelHeight.toInt()
    private val physicalWidth = 30f / Game.PPU
    private val physicalHeight = 10f / Game.PPU
    private val punchDistance = 28f / Game.PPU
    private val punchWidth = 16f / Game.PPU + (difficulty - 1f) / Game.PPU
    private val punchDepth = 10f / Game.PPU
    var health = initialHeath
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

    private val walk = animations.walk.copy()
    private val idle = animations.idle.copy()
    private val punch = animations.leftPunch.copy()
    private val prepare = animations.prepare.copy()
    private val hit = animations.hit.copy()

    //private val animations = assets.animation.punkAnimations
    private var currentMagicalAnimation: SignallingAnimationPlayer =
        walk
        set(value) {
            if (field != value) {
                value.restart()
                field = value
            }
        }

    private val fixture = body.createFixture(
        FixtureDef(
            shape = PolygonShape().apply {
                setAsBox(physicalWidth / 2f, physicalHeight / 2f)
            },
            filter = Filter().apply {
                categoryBits = ContactBits.ENEMY
                maskBits = ContactBits.WALL or ContactBits.REI_PUNCH or ContactBits.ENEMY
            },
            friction = 2f,
            userData = this
        )
    ) ?: error("Cat fixture is null! Should not happen!")


    private val tempVec2 = Vec2()
    private val leftPunchTargets = mutableSetOf<Player>()
    private val rightPunchTargets = mutableSetOf<Player>()
    private val leftPunchFixture = body.createFixture(
        FixtureDef(
            shape = PolygonShape().apply {
                setAsBox(punchWidth / 2f, punchDepth / 2f, center = tempVec2.set(-punchDistance, 0f), angle = 0f.radians)
            },
            filter = Filter().apply {
                categoryBits = ContactBits.ENEMY_PUNCH
                maskBits = ContactBits.REI
            },
            userData = leftPunchTargets,
            isSensor = true
        )
    ) ?: error("Fixture is null! Should not happen!")
    private val rightPunchFixture = body.createFixture(
        FixtureDef(
            shape = PolygonShape().apply {
                setAsBox(punchWidth / 2f, punchDepth / 2f, center = tempVec2.set(punchDistance, 0f), angle = 0f.radians)
            },
            filter = Filter().apply {
                categoryBits = ContactBits.ENEMY_PUNCH
                maskBits = ContactBits.REI
            },
            userData = rightPunchTargets,
            isSensor = true
        )
    ) ?: error("Fixture is null! Should not happen!")

    private val tempVec2f = MutableVec2f()
    private val tempVec2f2 = MutableVec2f()
    private val zeroVec2 = Vec2()
    private val tempColor = MutableColor()

    private var isFacingLeft = false
    private var wasPunching = false
    private var nextLeftPunch = true
    private var punchCooldown = 0f
    private var hitCooldown = 0f
    var isAggressive = false

    fun hit(from: Vec2, strong: Boolean) {
        if (health == 0) {
            return
        }
        health -= 1
        hitCooldown = if (health == 0) 500f else 300f
        currentMagicalAnimation = hit
        if (strong || health == 0) {
            val direction = tempVec2f.set(body.position.x, body.position.y).subtract(from.x, from.y)
            val force = if (health == 0) 3f else 5f / difficulty
            tempVec2f2.set(force, 0f)
            val rotation = direction.angleTo(tempVec2f2)
            if (health > 0) {
                val distance = direction.length()
                tempVec2f2.scale(1f / distance)
                tempVec2f2.x = min(2f / difficulty, tempVec2f2.x)
            }
            tempVec2f2.rotate(rotation)
            body.linearVelocity.set(tempVec2f2.x, tempVec2f2.y)
            body.isAwake = true
        }
        //body.applyLinearImpulse(tempVec2, body.position, true)
    }

    private fun onAnimationEvent(event: String) {
        when (event) {
            "enemyPunch" -> {
                if (isFacingLeft && leftPunchTargets.isNotEmpty() || !isFacingLeft && rightPunchTargets.isNotEmpty()) {
                    player.hit(body.position, difficulty)
                }
            }
        }
    }

    private fun stopBody() {
        body.linearVelocity.set(0f, 0f)
        tempVec2.set(body.position)
        tempVec2.x = (tempVec2.x * Game.PPU).toInt().toFloat() * Game.IPPU
        tempVec2.y = (tempVec2.y * Game.PPU).toInt().toFloat() * Game.IPPU
        body.setTransform(tempVec2, Angle.ZERO)
    }

    override fun update(dt: Duration, millis: Float, toBeat: Float, toMeasure: Float) {
        if (!canAct()) {
            stopBody()
            currentMagicalAnimation.update(dt)
            return
        }
        if (hitCooldown > 0f) {
            punchCooldown = 0f
            hitCooldown -= millis
            if (hitCooldown > 0f) {
                currentMagicalAnimation.update(dt)
                return
            }
        }
        if (health == 0) {
            health = -1
            onDeath(this)
            return
        }
        if (punchCooldown > 0f) {
            stopBody()
            punchCooldown -= millis
            if (punchCooldown > 0f) {
                currentMagicalAnimation.update(dt, ::onAnimationEvent)
                return
            }
        }

        if (leftPunchTargets.isNotEmpty()) {
            isFacingLeft = true
            currentMagicalAnimation = punch
            punchCooldown = 2000f
            currentMagicalAnimation.update(dt, ::onAnimationEvent)
            return
        } else if (rightPunchTargets.isNotEmpty()) {
            isFacingLeft = false
            currentMagicalAnimation = punch
            punchCooldown = 2000f
            currentMagicalAnimation.update(dt, ::onAnimationEvent)
            return
        }

        val inverseBeat = 1f - toBeat
        val direction = tempVec2f2.set(player.x - x, player.y - y)
        if (player.x < x) {
            direction.x += punchDistance
        } else {
            direction.x -= punchDistance
        }
        /*if (distance < 1.5f) {
            isAggressive = true
        }*/
        direction
            .scale(inverseBeat * inverseBeat * inverseBeat)
        val length = direction.length()
        val maxSpeed = 2f + (difficulty - 1f) * 0.5f
        //if (length > maxSpeed) {
            direction.setLength(maxSpeed).scale(inverseBeat * inverseBeat * inverseBeat)
        //}
        val speed = direction.length() / 100f * Game.PPU

        if (direction.x != 0f) {
            if (isFacingLeft && direction.x > 0f) {
                isFacingLeft = false
            } else if (!isFacingLeft && direction.x < 0f) {
                isFacingLeft = true
            }
        }

        if (direction.x == 0f && direction.y == 0f || !isAggressive) {
            currentMagicalAnimation = idle
            stopBody()
        } else if (speed != 0f) {
            currentMagicalAnimation = walk
            body.linearVelocity.set(direction.x, direction.y)
            body.isAwake = true
        }
        currentMagicalAnimation.update(dt * speed.toDouble(), ::onAnimationEvent)
    }

    private var shaperRenderer: ShapeRenderer? = null

    override fun render(batch: Batch) {
        currentMagicalAnimation.currentKeyFrame?.let { frame ->
            val width = frame.width / Game.PPU
            val height = frame.height / Game.PPU
            val positionX = texturePositionX(width)
            val positionY = texturePositionY(height)
            batch.draw(
                frame,
                positionX,
                positionY,
                width = width,
                height = height,
                flipX = isFacingLeft
            )
        }
        if (shaperRenderer == null) {
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
            x = body.position.x - physicalWidth / 2f,
            y = body.position.y - physicalHeight / 2f,
            width = physicalWidth,
            height = physicalHeight,
            color = Color.BLUE.toFloatBits(),
        )
    }

    private fun texturePositionX(width: Float) = body.position.x - width / 2f
    private fun texturePositionY(height: Float) = body.position.y - height
}