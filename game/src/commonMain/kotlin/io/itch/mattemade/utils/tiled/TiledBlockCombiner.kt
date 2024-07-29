package io.itch.mattemade.utils.tiled

import com.littlekt.graphics.g2d.tilemap.tiled.TiledMap
import com.littlekt.graphics.g2d.tilemap.tiled.TiledTilesLayer
import com.littlekt.math.Rect

object TiledBlockCombiner {

    fun TiledMap.combine(onSomethingCombined: (name: String, rect: Rect) -> Unit) {

        val objectsAssociatedWithTiles: Map<Int, TiledMap.Object> by lazy {
            val result = mutableMapOf<Int, TiledMap.Object>()
            tileSets.forEach { tileSet ->
                tileSet.tiles.forEach { tile ->
                    tile.objectGroup?.objects?.firstOrNull()?.let { firstObject ->
                        result += tile.id to firstObject
                    }
                }
            }
            result
        }

        layers.filterIsInstance<TiledTilesLayer>().associate { tilesLayer ->
            val visited = Array(tilesLayer.width) { BooleanArray(tilesLayer.height) }
            for (x in 0 until tilesLayer.width) {
                for (y in 0 until tilesLayer.height) {
                    visitVertical(
                        objectsAssociatedWithTiles,
                        tilesLayer,
                        x,
                        y,
                        visited,
                        lastKnownName = null,
                    )?.let { (name, verticalRange): Pair<String, IntRange> ->
                        visitHorizontal(
                            objectsAssociatedWithTiles,
                            tilesLayer,
                            x,
                            verticalRange,
                            visited,
                            name,
                        ).let { horizontalRange ->
                            onSomethingCombined(
                                name,
                                Rect(
                                    horizontalRange.first.toFloat(),
                                    verticalRange.first.toFloat(),
                                    (horizontalRange.last + 1 - horizontalRange.first).toFloat(),
                                    (verticalRange.last + 1 - verticalRange.first).toFloat()
                                )
                            )
                        }
                    }

                }
            }
            tilesLayer.id to 0
        }
    }

    private fun visitVertical(
        objectsAssociatedWithTiles: Map<Int, TiledMap.Object>,
        visibleLayer: TiledTilesLayer,
        x: Int,
        y: Int,
        visited: Array<BooleanArray>,
        lastKnownName: String? = null,
    ): Pair<String, IntRange>? {
        if (x < 0 || x >= visibleLayer.width || y < 0 || y >= visibleLayer.height || visited[x][y]) {
            return null
        }

        var nameIsDifferent = false
        return objectsAssociatedWithTiles[visibleLayer.getTileId(x, y)]?.let {

            if (lastKnownName != null && lastKnownName != it.name) {
                nameIsDifferent = true
                null
            } else {
                visited[x][y] = true
                (it.name to IntRange(y, y))
                    .merge(
                        visitVertical(
                            objectsAssociatedWithTiles,
                            visibleLayer,
                            x,
                            y - 1,
                            visited,
                            lastKnownName = it.name
                        )
                    )
                    .merge(
                        visitVertical(
                            objectsAssociatedWithTiles,
                            visibleLayer,
                            x,
                            y + 1,
                            visited,
                            lastKnownName = it.name
                        )
                    )
            }
        } ?: run {
            if (!nameIsDifferent) {
                visited[x][y] = true
            }
            null
        }
    }

    private fun visitHorizontal(
        objectsAssociatedWithTiles: Map<Int, TiledMap.Object>,
        visibleLayer: TiledTilesLayer,
        initialX: Int,
        verticalRange: IntRange,
        visited: Array<BooleanArray>,
        lastKnownName: String,
    ): IntRange {
        var x = initialX
        while (++x < visibleLayer.width) { // starting with x+1 to avoid checking the first column
            // check if the right column could be merged
            for (y in verticalRange) {
                val firstObject = objectsAssociatedWithTiles[visibleLayer.getTileId(x, y)]
                if (firstObject == null || firstObject.name != lastKnownName) {
                    return IntRange(initialX, x - 1)
                }
            }
            // mark the right column as visited
            for (y in verticalRange) {
                visited[x][y] = true
            }
        }

        return IntRange(initialX, x - 1)
    }

    private fun Pair<String, IntRange>.merge(other: Pair<*, IntRange>?): Pair<String, IntRange> =
        if (other == null) this else this.first to IntRange(
            kotlin.math.min(this.second.first, other.second.first),
            kotlin.math.max(this.second.last, other.second.last)
        )

    private fun IntRange.merge(other: IntRange?): IntRange =
        if (other == null) this else IntRange(
            kotlin.math.min(this.first, other.first),
            kotlin.math.max(this.last, other.last)
        )

}
