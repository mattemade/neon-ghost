package io.itch.mattemade.neonghost.world

import com.littlekt.graphics.g2d.tilemap.tiled.TiledMap
import io.itch.mattemade.neonghost.Game
import com.littlekt.math.Rect
import com.littlekt.math.Vec2f
import org.jbox2d.collision.shapes.ChainShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.Filter
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World

class Wall(world: World, val x: Float, val y: Float, val shape: TiledMap.Object.Shape, private val mapHeight: Int) {

    private val body = world.createBody(
        BodyDef(
        type = BodyType.STATIC,
    ).apply {
        position.set(x * Game.IPPU, (mapHeight - y) * Game.IPPU)
    }).apply {
        createFixture(
            FixtureDef(
            shape = ChainShape().apply {
                val points = shape.toPoints()
                createLoop(
                    points,
                    points.size
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

    private fun p(x: Float, y: Float) = Vec2(x, y).mulLocal(Game.IPPU)

    private fun TiledMap.Object.Shape.toPoints(): Array<Vec2> {
        return when (this) {
            is TiledMap.Object.Shape.Rectangle -> {
                arrayOf(
                    p(0f, 0f),
                    p(width, 0f),
                    p(width, height),
                    p(0f, height),
                )
            }

            is TiledMap.Object.Shape.Ellipse -> TODO()
            TiledMap.Object.Shape.Point -> TODO()
            is TiledMap.Object.Shape.Polygon -> points.map { p(it.x,  -it.y) }.toTypedArray()
            is TiledMap.Object.Shape.Polyline -> points.map { p(it.x, -it.y) }.toTypedArray()

            is TiledMap.Object.Shape.Text -> TODO()
        }
    }
}
