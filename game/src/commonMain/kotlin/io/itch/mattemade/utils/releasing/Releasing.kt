package io.itch.mattemade.utils.releasing

import com.littlekt.GameAsset
import com.littlekt.PreparableGameAsset
import com.littlekt.Releasable

interface Releasing : Releasable {
    fun <T : Releasable> remember(block: () -> T): Lazy<T>
    fun <T : Releasable> T.releasing(): T
    fun <T : Releasable, U : GameAsset<T>> U.releasing(): AutoDisposingGameAsset<T>
    fun <T : Releasable, U : PreparableGameAsset<T>> U.releasing(): AutoDisposingPreparableGameAsset<T>
    fun <T : Releasable> managed(block: () -> T): T
    fun <K> K.registerAsContextDisposer(applicableTo: Any, block: K.(Any?) -> Unit): K
    fun <T : Releasable> forget(disposable: T)
}
