package io.itch.mattemade.neonghost.world

import com.littlekt.graphics.g2d.tilemap.tiled.TiledMap
import io.itch.mattemade.neonghost.Game
import com.littlekt.math.Rect
import com.littlekt.math.Vec2f
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.Filter
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World

class Trigger(
    private val world: World,
    val rect: Rect,
    mapHeight: Int,
    val name: String,
    val properties: Map<String, TiledMap.Property>,
): Releasing by Self() {

    val renderPath = mutableListOf<Vec2f>()
    private val body = world.createBody(
        BodyDef(
            type = BodyType.STATIC,
        ).apply {
            position.set(rect.x + rect.width / 2f, mapHeight - rect.y + rect.height / 2f).mulLocal(
                Game.IPPU)
        }).apply {
        createFixture(
            FixtureDef(
                shape = PolygonShape().apply {
                    setAsBox(rect.width / 2f / Game.PPU, rect.height / 2f / Game.PPU)
                },
                friction = 0f,
                filter = Filter().apply {
                    categoryBits = ContactBits.TRIGGER
                },
                userData = this@Trigger,
                isSensor = true
            )
        )
    }.rememberTo { world.destroyBody(it) }
}