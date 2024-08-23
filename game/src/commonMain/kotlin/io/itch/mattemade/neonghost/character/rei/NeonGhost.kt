package io.itch.mattemade.neonghost.character.rei

import com.littlekt.file.Vfs
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.ParticleSimulator
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.input.InputMapController
import com.littlekt.math.MutableVec2f
import com.soywiz.korma.geom.Angle
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.character.DepthBasedRenderable
import io.itch.mattemade.neonghost.character.rei.Player.Companion.magicGirlShadowColor
import io.itch.mattemade.neonghost.character.rei.Player.Companion.magicGirlShadowEdgeColor
import io.itch.mattemade.neonghost.character.rei.Player.Companion.spellRx
import io.itch.mattemade.neonghost.character.rei.Player.Companion.spellRy
import io.itch.mattemade.neonghost.pixelPerfectPosition
import io.itch.mattemade.neonghost.scene.GhostOverlay
import io.itch.mattemade.neonghost.world.CameraMan
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
import kotlin.time.Duration

class NeonGhost(
    initialPosition: Vec2,
    initiallyFacingLeft: Boolean,
    private val neonGhostWorld: World,
    private val assets: Assets,
    private val controller: InputMapController<GameInput>,
    private val particleSimulator: ParticleSimulator,
    private val vfs: Vfs,
    private val ghostOverlay: GhostOverlay,
    private val cameraMan: CameraMan,
    private val removeGhost: (NeonGhost) -> Unit,
) : Releasing by Self(),
    DepthBasedRenderable {

    //private val textureSizeInWorldUnits = Vec2(60f / Game.PPU, 96f / Game.PPU)
    val pixelWidth = 1f//textureSizeInWorldUnits.x
    val pixelWidthInt = pixelWidth.toInt()
    val pixelHeight = 1f//textureSizeInWorldUnits.y
    val pixelHeightInt = pixelHeight.toInt()
    private val physicalHw = 1f / Game.PPU
    private val physicalHh = 1f / Game.PPU


    private val body = neonGhostWorld.createBody(
        BodyDef(
            type = BodyType.DYNAMIC,
            userData = this,
        ).apply {
            position.set(initialPosition)
        }
    ).rememberTo { neonGhostWorld.destroyBody(it) }

    val x get() = body.position.x
    val y get() = body.position.y
    override val depth: Float
        get() = y

    private var animations = assets.animation.magicalReiAnimations

    internal var currentAnimation: SignallingAnimationPlayer = animations.prepare
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

    private val zeroVec2 = Vec2()
    private val tempColor = MutableColor()

    private var isFacingLeft = initiallyFacingLeft
    private var punchCooldown = 0f

    private var activatePunch = false
    fun activatePunch() {
        activatePunch = true
    }

    private var dead = false

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
        val xMovement = controller.axis(GameInput.HORIZONTAL)
        val yMovement = controller.axis(GameInput.VERTICAL)
        val moving = xMovement != 0f || yMovement != 0f
        if (punchCooldown > 0f) {
            if (body.linearVelocity.x != 0f || body.linearVelocity.y != 0f) {
                stopBody()
            }
            punchCooldown -= millis
            if (punchCooldown > 0f) {
                currentAnimation.update(notAdjustedDt)
                return
            }
            ghostOverlay.renderNeonGhost(null, isFacingLeft, 0f, 0f)
            removeGhost(this)
            dead = true
        }

        var speed = 1f
        if (moving) {
            currentAnimation = animations.walk
            if (isFacingLeft && xMovement > 0f) {
                isFacingLeft = false
            } else if (!isFacingLeft && xMovement < 0f) {
                isFacingLeft = true
            }
            body.linearVelocity.set(xMovement, yMovement)
            speed = body.linearVelocity.length()
            body.isAwake = true
        } else {
            if (currentAnimation != animations.prepare) {
                currentAnimation = animations.idle
            }
            stopBody()
        }

        if (controller.down(GameInput.MAGIC)) {
            if (controller.pressed(GameInput.ATTACK)) {
                punchCooldown = 300f
                stopBody()
                currentAnimation = animations.leftPunch
            }
        } else {
            punchCooldown = 300f
            stopBody()
            // TODO: cast ghost AOE
            // 1. create a AOE fixture in normal world of RX circle
            // 2. destroy the ghost
            // 3. on contact, check if enemies within the range are in the elliplse
            // 4. on next normal world update, hit all the enemies within the range
        }

        currentAnimation.update(notAdjustedDt * speed.toDouble()) // will trigger animation callbacks

        if (activatePunch) {
            activatePunch = false
            // TODO: cast ghost projectile
            // 1. add it in the normal world
            /*if (isFacingLeft) {
                leftPunchTargets.forEach {
                    it.hit(body.position, if (movingToBeat) 2 else 1)
                }
            } else {
                rightPunchTargets.forEach {
                    it.hit(body.position, if (movingToBeat) 2 else 1)
                }
            }*/
        }
    }

    override fun render(batch: Batch) {
        if (dead) {
            return
        }
        currentAnimation.currentKeyFrame?.let { frame ->
            ghostOverlay.renderNeonGhost(
                frame,
                isFacingLeft,
                body.position.x - cameraMan.position.x + Game.visibleWorldWidth / 2f,
                body.position.y - cameraMan.position.y + Game.visibleWorldHeight / 2f
            )
        }

    }


    override fun renderShadow(shapeRenderer: ShapeRenderer) {
        currentAnimation.currentKeyFrame?.let { frame ->
            for (i in 0 until castsToStopTime) {
                shapeRenderer.filledEllipse(
                    x = body.position.x.pixelPerfectPosition,
                    y = body.position.y.pixelPerfectPosition,
                    rx = spellRx,
                    ry = spellRy,
                    innerColor = magicGirlShadowColor,
                    outerColor = magicGirlShadowColor,
                )
            }
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

    private fun texturePositionX(width: Float) = body.position.x - width / 2f
    private fun texturePositionY(height: Float) = body.position.y - height

    companion object {
        private val castTime = 2f
        private val reduceTime = 0.1f
        private const val castBeforeSlowingTime = 1
        private const val castsToStopTime = 2
    }

}