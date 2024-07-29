package io.itch.mattemade.utils.releasing

import com.littlekt.GameAsset
import com.littlekt.PreparableGameAsset
import com.littlekt.Releasable

internal class Self : Releasing {
    private val disposables = mutableSetOf<Releasable>()
    private val contextReleasers = mutableMapOf<Any, (Any?) -> Unit>()

    override fun <T : Releasable> remember(block: () -> T): Lazy<T> =
        lazy { managed(block) }

    override fun <T : Releasable> T.releasing(): T =
        this.also { disposables += it }

    override fun <T : Releasable, U : GameAsset<T>> U.releasing(): AutoDisposingGameAsset<T> =
        AutoDisposingGameAsset(this).releasing()

    override fun <T : Releasable, U : PreparableGameAsset<T>> U.releasing(): AutoDisposingPreparableGameAsset<T> =
        AutoDisposingPreparableGameAsset(this).releasing()

    override fun <T : Releasable> managed(block: () -> T): T =
        block().releasing()

    override fun <K> K.registerAsContextDisposer(applicableTo: Any, block: K.(Any?) -> Unit): K {
        contextReleasers[applicableTo] = { this.block(it) }
        return this
    }

    override fun <T : Releasable> forget(disposable: T) {
        disposables.remove(disposable)
    }

    override fun release() {
        disposables.forEach {
            if (it is HasContext<*>) {
                it.context.forEach { (clazz, context) ->
                    contextReleasers[clazz]?.invoke(context)
                }
            }

            it.release()
        }
        disposables.clear()
    }
}
