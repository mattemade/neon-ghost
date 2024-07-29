package io.itch.mattemade.utils.releasing

import com.littlekt.PreparableGameAsset
import com.littlekt.Releasable
import kotlin.reflect.KProperty

class AutoDisposingPreparableGameAsset<T : Releasable>(private val gameAsset: PreparableGameAsset<T>) :
    Releasable {

    private var content: T? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
        gameAsset.getValue(thisRef, property).also {
            val oldContent = content
            if (oldContent != it) {
                oldContent?.release()
                content = it
            }
        }

    override fun release() {
        content?.release()
    }
}
