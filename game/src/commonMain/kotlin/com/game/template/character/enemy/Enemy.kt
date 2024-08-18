package com.game.template.character.enemy

import com.game.template.Assets
import com.game.template.CharacterAnimations
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
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration

class Enemy(
    initialPosition: Vec2,
    private val player: Player,
    private val world: World,
    private val assets: Assets,
    private val animations: CharacterAnimations,
    private val controller: InputMapController<GameInput>,
    private val particleSimulator: ParticleSimulator,
    private val vfs: Vfs,
    private val maxSpeed: Float = 1f
) : Releasing by Self(),
    HasContext<Body>,
    DepthBasedRenderable {

    val pixelWidth = 1f//textureSizeInWorldUnits.x
    val pixelWidthInt = pixelWidth.toInt()
    val pixelHeight = 1f//textureSizeInWorldUnits.y
    val pixelHeightInt = pixelHeight.toInt()
    private val physicalHw = 20f / Game.PPU
    private val physicalHh = 5f / Game.PPU


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

    //private val animations = assets.animation.punkAnimations
    private var currentMagicalAnimation: SignallingAnimationPlayer =
        animations.walk
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
                maskBits = ContactBits.WALL or ContactBits.REI_PUNCH or ContactBits.ENEMY
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
    private var isAggressive = false

    fun hit(from: Vec2, strong: Boolean) {
        hitCooldown = 300f
        currentMagicalAnimation = animations.hit
        if (strong) {
            val direction = tempVec2f.set(body.position.x, body.position.y).subtract(from.x, from.y)
            tempVec2f2.set(1f, 0f)
            val distance = direction.length()
            val rotation = direction.angleTo(tempVec2f2)
            println("distance: $distance")
            tempVec2f2.scale(1f / distance / maxSpeed)
            tempVec2f2.x = min(2f, tempVec2f2.x)
            tempVec2f2.rotate(rotation)
            println("move: $tempVec2f2")
            body.linearVelocity.set(tempVec2f2.x, tempVec2f2.y)
            body.isAwake = true
        }
        //body.applyLinearImpulse(tempVec2, body.position, true)
    }

    override fun update(dt: Duration, millis: Float, toBeat: Float, toMeasure: Float) {
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
            if (hitCooldown > 0f) {
                currentMagicalAnimation.update(dt)
                return
            }
        }
        val inverseBeat = 1f - toBeat
        val direction = tempVec2f2.set(player.x - x, player.y - y)
        val distance = tempVec2f2.length()
        if (distance < 1.5f) {
            isAggressive = true
        }
        direction
            .scale(inverseBeat * inverseBeat * inverseBeat)
        val length = direction.length()
        if (length > maxSpeed) {
            direction.scale(1f / length).scale(maxSpeed)
        }
        val speed = direction.length() / 100f * Game.PPU

        if (direction.x != 0f) {
            if (isFacingLeft && direction.x > 0f) {
                isFacingLeft = false
            } else if (!isFacingLeft && direction.x < 0f) {
                isFacingLeft = true
            }
        }

        if (direction.x == 0f && direction.y == 0f || distance < 0.5f || !isAggressive) {
            currentMagicalAnimation = animations.idle
            body.linearVelocity.set(0f, 0f)
        } else if (speed != 0f) {
            currentMagicalAnimation = animations.walk
            body.linearVelocity.set(direction.x, direction.y)
            body.isAwake = true
        }
        currentMagicalAnimation.update(dt * speed.toDouble() )
    }

    override fun render(batch: Batch) {
        currentMagicalAnimation.currentKeyFrame?.let { frame ->
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