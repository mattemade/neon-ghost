package io.itch.mattemade.utils.asset

import com.littlekt.AssetProvider
import com.littlekt.Context
import com.littlekt.PreparableGameAsset
import com.littlekt.Releasable
import io.itch.mattemade.utils.animation.SignallingAnimationPlayer
import io.itch.mattemade.utils.animation.readAnimationPlayer
import io.itch.mattemade.utils.atlas.RuntimePacker
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self

open class AssetPack(protected val context: Context, private val defaultAnimationCallback: ((String) -> Unit)? = null) :
    Releasing by Self() {

    private val providers = mutableListOf<AssetProvider>()
    protected val provider
        get() = AssetProvider(context).also { providers += it }

    private var providerWasFullyLoaded = false
    val isLoaded: Boolean
        get() =
            if (providerWasFullyLoaded) {
                true
            } else {
                var result = true
                providers.forEach {
                    if (!it.fullyLoaded) {
                        it.update()
                        result = false
                    }
                }
                providerWasFullyLoaded = result
                result
            }

    fun <T : Any> preparePlain(action: suspend () -> T): PreparableGameAsset<T> =
        provider.prepare { action() }

    fun <T : Releasable> prepare(action: suspend () -> T): PreparableGameAsset<T> =
        provider.prepare { action().releasing() }

    protected fun String.prepareAnimationPlayer(runtimePacker: RuntimePacker, callback: ((String) -> Unit)? = defaultAnimationCallback): PreparableGameAsset<SignallingAnimationPlayer> =
        provider.prepare { this.readAnimationPlayer(runtimePacker, callback) }

    protected suspend fun String.readAnimationPlayer(runtimePacker: RuntimePacker, callback: ((String) -> Unit)? = defaultAnimationCallback): SignallingAnimationPlayer =
        context.resourcesVfs[this].readAnimationPlayer(runtimePacker, callback) { releasing() }

    fun <T : AssetPack> T.packed(): T {
        this@AssetPack.providers.addAll(providers)
        return this
    }
}
