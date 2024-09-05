package io.itch.mattemade.neonghost.character

import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.CharacterAnimations
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.character.DepthBasedRenderable
import io.itch.mattemade.neonghost.character.rei.Player
import io.itch.mattemade.neonghost.world.ContactBits
import com.littlekt.file.Vfs
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.ParticleSimulator
import com.littlekt.graphics.g2d.TextureSlice
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.input.InputMapController
import com.littlekt.math.MutableVec2f
import com.littlekt.math.Vec2f
import com.soywiz.korma.geom.Angle
import com.soywiz.korma.geom.radians
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.neonghost.pixelPerfectPosition
import io.itch.mattemade.neonghost.tempo.Choreographer
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

class VisibleObject(
    val position: Vec2f,
    val slice: TextureSlice,
    override val depth: Float,
) : Releasing by Self(),
    DepthBasedRenderable {

    private val width = slice.width * Game.IPPU
    private val height = slice.height * Game.IPPU
    private val texturePosition = Vec2f(
        (position.x - width / 2f).pixelPerfectPosition,
        (position.y - height).pixelPerfectPosition
    )

    override fun update(dt: Duration, millis: Float, notAdjustedDt: Duration, toBeat: Float, toMeasure: Float, isFighting: Boolean) {

    }

    override fun render(batch: Batch) {
        batch.draw(
            slice,
            x = texturePosition.x,
            y = texturePosition.y,
            width = width,
            height = height
        )
    }
    override fun renderShadow(shapeRenderer: ShapeRenderer) {

    }
}