package io.itch.mattemade.utils.asset

import co.touchlab.stately.collections.ConcurrentMutableList
import com.game.template.Assets
import com.littlekt.AssetProvider
import com.littlekt.Context
import com.littlekt.PreparableGameAsset
import com.littlekt.Releasable
import com.soywiz.kds.FastIntMap
import com.soywiz.kds.fastForEach
import com.soywiz.kds.fastValueForEach
import com.soywiz.kds.get
import com.soywiz.kds.getOrPut
import com.soywiz.kds.values
import io.itch.mattemade.utils.animation.SignallingAnimationPlayer
import io.itch.mattemade.utils.animation.readAnimationPlayer
import io.itch.mattemade.utils.atlas.RuntimeTextureAtlasPacker
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self

open class AssetPack(protected val context: Context, private val defaultAnimationCallback: ((String) -> Unit)? = null) :
    Releasing by Self() {

    private var currentOrder = 0
    private val orderedProviders = FastIntMap<MutableList<AssetProvider>>()
    //private val providers = mutableListOf<AssetProvider>()

    // create a new provider each time, so each asset could be loaded independently
    private fun createProvider(order: Int = 0): AssetProvider =
        AssetProvider(context).also { orderedProviders.getOrPut(order) { mutableListOf() } += it }

    private var providerWasFullyLoaded = false
    val isLoaded: Boolean
        get() =
            if (providerWasFullyLoaded) {
                true
            } else {
                var result = true
                var stop = false
                while (result && !stop) {
                    val assetProviders = orderedProviders[currentOrder]
                    val currentProvidersCount = assetProviders?.size ?: 0
                    if (currentProvidersCount == 0) {
                        providerWasFullyLoaded = true
                        stop = true
                    } else {
                        for (i in 0 until currentProvidersCount) {
                            assetProviders?.get(i)?.update()
                        }
                        assetProviders?.forEach {
                            if (!it.fullyLoaded) {
                                it.update()
                                result = false
                            }
                        }
                        if (result) {
                            currentOrder++
                        } else {
                            stop = true
                        }
                    }
                }
                result
            }

    fun <T : Any> preparePlain(order: Int = 0, action: suspend () -> T): PreparableGameAsset<T> =
        createProvider(order).prepare { action() }

    fun <T : Releasable> prepare(order: Int = 0, action: suspend () -> T): PreparableGameAsset<T> =
        createProvider(order).prepare { action().releasing() }

    protected fun String.prepareAnimationPlayer(runtimeTextureAtlasPacker: RuntimeTextureAtlasPacker, order: Int = 0, callback: ((String) -> Unit)? = defaultAnimationCallback): PreparableGameAsset<SignallingAnimationPlayer> =
        createProvider(order).prepare { this.readAnimationPlayer(runtimeTextureAtlasPacker, callback) }

    protected suspend fun String.readAnimationPlayer(runtimeTextureAtlasPacker: RuntimeTextureAtlasPacker, callback: ((String) -> Unit)? = defaultAnimationCallback): SignallingAnimationPlayer =
        context.resourcesVfs[this].readAnimationPlayer(runtimeTextureAtlasPacker, callback)

    fun <T : AssetPack> T.packed(order: Int = 0): T {
        this@AssetPack.orderedProviders.getOrPut(order) { mutableListOf() }.apply {
            orderedProviders.fastValueForEach { addAll(it) }
        }
        return this
    }

    fun <T : AssetPack> pack(order: Int = 0, action: suspend () -> T): PreparableGameAsset<T> =
        createProvider(order).prepare { action().packed(order).releasing() }
}
