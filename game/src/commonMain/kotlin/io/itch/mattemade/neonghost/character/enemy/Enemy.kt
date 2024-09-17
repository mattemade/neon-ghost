package io.itch.mattemade.neonghost.character.enemy

import com.littlekt.file.Vfs
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.ParticleSimulator
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.math.MutableVec2f
import com.soywiz.korma.geom.Angle
import com.soywiz.korma.geom.radians
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.CharacterAnimations
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.character.DepthBasedRenderable
import io.itch.mattemade.neonghost.character.rei.Player
import io.itch.mattemade.neonghost.pixelPerfectPosition
import io.itch.mattemade.neonghost.tempo.Choreographer
import io.itch.mattemade.neonghost.touch.CombinedInput
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
import kotlin.time.Duration

class Enemy(
    val name: String? = null,
    initialPosition: Vec2,
    private val player: Player,
    private val world: World,
    private val choreographer: Choreographer,
    private val assets: Assets,
    animations: CharacterAnimations,
    private val controller: CombinedInput,
    private val particleSimulator: ParticleSimulator,
    private val vfs: Vfs,
    private val difficulty: Float = 1f,
    val initialHeath: Int = 3,
    val initialFacingLeft: Boolean = false,
    private val canAct: () -> Boolean,
    private val onDeath: (Enemy) -> Unit,
    private val onBecomingAggessive: (Enemy) -> Unit,
    val isBoss: Boolean,
) : Releasing by Self(),
    DepthBasedRenderable {
    private val isDummy = name == "dummy"

    val pixelWidth = 1f//textureSizeInWorldUnits.x
    val pixelWidthInt = pixelWidth.toInt()
    val pixelHeight = 1f//textureSizeInWorldUnits.y
    val pixelHeightInt = pixelHeight.toInt()
    private val physicalWidth = 30f / Game.PPU
    private val physicalHeight = 10f / Game.PPU
    private val punchDistance = defaultPunchDistance
    private val punchWidth = 16f / Game.PPU + (difficulty - 1f) / Game.PPU
    private val punchDepth = 10f / Game.PPU
    var health = initialHeath
        private set

    val body = world.createBody(
        BodyDef(
            type = BodyType.DYNAMIC,
            userData = this,
        ).apply {
            position.set(initialPosition)
        }
    ).rememberTo { world.destroyBody(it) }

    val x get() = body.position.x
    val y get() = body.position.y
    var extraForEllipseCheck = physicalWidth / 2f
    override val depth: Float get() = y

    private val walk = animations.walk.copy()
    private val idle = animations.idle.copy()
    private val punch = animations.leftPunch.copy()
    private val prepare = animations.prepare.copy()
    private val hit = animations.hit.copy()

    //private val animations = assets.animation.punkAnimations
    private var currentMagicalAnimation: SignallingAnimationPlayer =
        idle
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
                maskBits =
                    ContactBits.WALL or ContactBits.REI_PUNCH /*or ContactBits.ENEMY*/ or ContactBits.GHOST_AOE
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
                setAsBox(
                    punchWidth / 2f,
                    punchDepth / 2f,
                    center = tempVec2.set(-punchDistance, 0f),
                    angle = 0f.radians
                )
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
                setAsBox(
                    punchWidth / 2f,
                    punchDepth / 2f,
                    center = tempVec2.set(punchDistance, 0f),
                    angle = 0f.radians
                )
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

    private var isFacingLeft = initialFacingLeft
    private var wasPunching = false
    private var nextLeftPunch = true
    private var punchCooldown = 0f
    var hitCooldown = 0f
    var hitsBeforeIgnoreCooldown = calcHitsBeforeCooldown(difficulty)
    var isAggressive = false
    var target: EnemyAi.TargetPoint? = null
        set(value) {
            field?.occupiedBy = null
            value?.occupiedBy = this
            field = value
        }

    init {
        if (isDummy) {
            target = EnemyAi.TargetPoint(player, x, y)
        }
    }

    fun calcHitsBeforeCooldown(difficulty: Float): Int {
        return if (difficulty > 4f) 0 else if (difficulty > 3f) 2 else 3
    }

    fun hit(from: Vec2, strength: Int, fromSpell: Boolean = false) {
        if (health == 0) {
            return
        }
        if (!isAggressive && !isDummy) {
            isAggressive = true
            onBecomingAggessive(this)
        }

        health = max(0, health - strength)
        if (health == 0) {
            choreographer.sound(assets.sound.enemyDeath.sound, body.position.x, body.position.y)
        } else {
            choreographer.sound(
                if (strength == 1) assets.sound.punch.sound else assets.sound.powerPunch.sound,
                body.position.x,
                body.position.y
            )
        }
        if (strength == 1) {
            hitsBeforeIgnoreCooldown--
        } else {
            hitsBeforeIgnoreCooldown = calcHitsBeforeCooldown(difficulty)
        }
        hitCooldown = if (health == 0) 500f else if (hitsBeforeIgnoreCooldown <= 0) 0f else 300f
        if (hitCooldown > 0f) {
            currentMagicalAnimation = hit
        }
        if (strength > 1 || health == 0 || fromSpell || isDummy) {
            if (!isDummy) {
                target = null
            }

            val direction = tempVec2f.set(body.position.x, body.position.y).subtract(from.x, from.y)
            val force = (if (isDummy) { // dummy will get knock back even with normal punch
                if (strength > 1) 1.5f else -10f
            } else {
                if (health == 0) 1.5f else 1.25f
            } + 10f) / (difficulty + 10f)
            tempVec2f2.set(force, 0f)
            val rotation = direction.angleTo(tempVec2f2)
            tempVec2f2.rotate(rotation)
            body.linearVelocity.set(tempVec2f2.x, tempVec2f2.y)
            body.isAwake = true
        }
    }

    private fun onAnimationEvent(event: String) {
        when (event) {
            "enemyPunch" -> {
                choreographer.sound(assets.sound.whoosh.sound, body.position.x, body.position.y)
                if (isFacingLeft && leftPunchTargets.isNotEmpty() || !isFacingLeft && rightPunchTargets.isNotEmpty()) {
                    player.hit(body.position, difficulty)
                }
                hitsBeforeIgnoreCooldown = calcHitsBeforeCooldown(difficulty)
            }

            "enemyFootstep" -> {
                choreographer.sound(assets.sound.footstep.sound, body.position.x, body.position.y)
                hitsBeforeIgnoreCooldown = calcHitsBeforeCooldown(difficulty)
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

    override fun update(
        dt: Duration,
        millis: Float,
        notAdjustedDt: Duration,
        toBeat: Float,
        toMeasure: Float,
        isFighting: Boolean
    ) {
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
            currentMagicalAnimation.restart()
            punchCooldown = 2000f
            currentMagicalAnimation.update(dt, ::onAnimationEvent)
            return
        } else if (rightPunchTargets.isNotEmpty()) {
            isFacingLeft = false
            currentMagicalAnimation = punch
            currentMagicalAnimation.restart()
            punchCooldown = 2000f
            currentMagicalAnimation.update(dt, ::onAnimationEvent)
            return
        }

        val inverseBeat = 1f - toBeat


        if (canAct() && !isAggressive && !isDummy) {
            val isFacingPlayer = isFacingLeft && player.x < x || !isFacingLeft && player.x > x
            if (isFacingPlayer) {
                if (tempVec2f2.set(player.x, player.y).subtract(x, y).length() < 1.5f) {
                    isAggressive = true
                    onBecomingAggessive(this)
                }
            }
        }
        var speed = 0f
        target?.let {
            tempVec2f2.set(it.position).subtract(x, y)
            val distanceToTarget = tempVec2f2.length()
            if (distanceToTarget > 0.1f) {
                val beatFactor = inverseBeat * inverseBeat * inverseBeat
                val maxSpeed = 2f + (difficulty - 1f) * 0.5f
                // move horizontally if to the opposite side of player
                if (x < it.position.x && player.x < it.position.x || x > it.position.x && player.x > it.position.x) {
                    tempVec2f2.y = 0f
                }
                tempVec2f2.setLength(maxSpeed).scale(beatFactor)
                if (tempVec2f2.length() / 120f > distanceToTarget) { // target is within 2 frames of simulation
                    tempVec2f2.setLength(distanceToTarget / 120f)
                }
                speed = distanceToTarget / 100f * Game.PPU
                if (speed != 0f) {
                    currentMagicalAnimation = walk
                    body.linearVelocity.set(tempVec2f2.x, tempVec2f2.y)
                    body.isAwake = true
                }
            } else {
                stopBody()
            }
            if (isFacingLeft && player.x > x) {
                isFacingLeft = false
            } else if (!isFacingLeft && player.x < x) {
                isFacingLeft = true
            }
            /*if (tempVec2f2.x != 0f) {

            }*/
        } ?: run {
            currentMagicalAnimation = idle
            stopBody()
        }

        currentMagicalAnimation.update(dt * speed.toDouble(), ::onAnimationEvent)
    }

    private var shaperRenderer: ShapeRenderer? = null

    override fun render(batch: Batch) {
        currentMagicalAnimation.currentKeyFrame?.let { frame ->
            val width = frame.width / Game.PPU
            val height = frame.height / Game.PPU
            val xOffset = (frame.width * 0.1f / Game.PPU).pixelPerfectPosition
            val yOffset = (3f / Game.PPU).pixelPerfectPosition
            val positionX =
                texturePositionX(width).pixelPerfectPosition + if (isFacingLeft) -xOffset else xOffset
            val positionY = texturePositionY(height) + yOffset
            batch.draw(
                frame,
                positionX,
                positionY,
                width = width,
                height = height,
                flipX = isFacingLeft
            )
        }
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
            x = body.position.x - physicalWidth / 2f,
            y = body.position.y - physicalHeight / 2f,
            width = physicalWidth,
            height = physicalHeight,
            color = Color.BLUE.toFloatBits(),
        )*/
    }

    override fun renderShadow(shapeRenderer: ShapeRenderer) {
        currentMagicalAnimation.currentKeyFrame?.let { frame ->
            val width = frame.width / Game.PPU
            shapeRenderer.filledEllipse(
                x = body.position.x,
                y = body.position.y,
                rx = width / 4f,
                ry = width / 8f,
                innerColor = Game.shadowColor,
                outerColor = Game.shadowColor,
            )
        }
    }

    private fun texturePositionX(width: Float) = body.position.x - width / 2f
    private fun texturePositionY(height: Float) = body.position.y - height

    val str = "$name"
    override fun toString(): String {
        return str
    }

    companion object {
        const val defaultPunchDistance = 28f / Game.PPU
        const val defaultHitsBeforeIgnoreCooldown = 3
    }
}