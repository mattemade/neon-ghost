package io.itch.mattemade.neonghost.world

import io.itch.mattemade.neonghost.Game
import com.littlekt.math.Rect
import com.littlekt.math.Vec2f
import org.jbox2d.collision.shapes.ChainShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.Filter
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import org.jbox2d.dynamics.contacts.Position

class Floor(world: World, val rect: Rect, mapHeight: Int) {

    val renderPath = mutableListOf<Vec2f>()
    private val body = world.createBody(
        BodyDef(
        type = BodyType.STATIC,
    ).apply {
        //position.set(0f, 0f)
    }).apply {
        createFixture(
            FixtureDef(
            shape = ChainShape().apply {
                createLoop(
                    arrayOf(
                        Vec2(rect.x, mapHeight - rect.y).mulLocal(Game.IPPU),
                        Vec2(rect.x + rect.width, mapHeight - rect.y).mulLocal(Game.IPPU),
                        Vec2(rect.x + rect.width + rect.height, mapHeight - rect.y + rect.height).mulLocal(
                            Game.IPPU),
                        Vec2(rect.x + rect.height, mapHeight - rect.y + rect.height).mulLocal(Game.IPPU)
                    ).also {
                        renderPath.addAll(it.map { Vec2f(it.x, it.y) })
                    },
                    4
                )
            },
            friction = 0f,
            filter = Filter().apply{
                categoryBits = ContactBits.WALL
            },
            userData = userData
        )
        )
    }
}