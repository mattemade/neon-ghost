package io.itch.mattemade.neonghost

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.littlekt.Context
import com.littlekt.audio.AudioClipEx
import com.littlekt.file.vfs.readAudioClipEx
import io.itch.mattemade.utils.asset.AssetPack

class ExtraAssets(context: Context) : AssetPack(context) {
    val music by pack(0) { ExtraMusic(context) }
    val sound by pack(1) { ExtraSound(context) }
}

class ExtraSound(context: Context) : AssetPack(context) {
    val powerUpLoop by pack {
        SoundPack(
            context, listOf(
                "sound/Powerup/Powerup - loop.wav",
            )
        )
    }

    /*val powerUpSingleShot by pack {
        SoundPack(
            context, listOf(
                "sound/Powerup/Powerup - single shot.wav",
            )
        )
    }*/
    val slowMo by pack {
        SoundPack(
            context, listOf(
                "sound/Powerup/Slow-mo.wav",
            )
        )
    }
    val reiVoice by pack {
        SoundPack(
            context, listOf(
                "sound/voice/rei1.wav",
                "sound/voice/rei2.wav",
            )
        )
    }
    val punkVoice by pack {
        SoundPack(
            context, listOf(
                "sound/voice/strange1.wav",
                "sound/voice/strange2.wav",
            )
        )
    }
    val guardVoice by pack {
        SoundPack(
            context, listOf(
                "sound/voice/low_middle1.wav",
                "sound/voice/low_middle2.wav",
            )
        )
    }
    val officerVoice by pack {
        SoundPack(
            context, listOf(
                "sound/voice/high_bass1.wav",
                "sound/voice/high_bass2.wav",
            )
        )
    }
    val terminalVoice by pack {
        SoundPack(
            context, listOf(
                "sound/voice/robot.wav",
            )
        )
    }
    val speech by preparePlain(1) {
        listOf(
            "rei" to reiVoice,
            "magic" to reiVoice,
            "punk" to punkVoice,
            "guard" to guardVoice,
            "officer" to officerVoice,
            "terminal" to terminalVoice,
            ).toMap()
    }

    val ghostSplash by pack {
        SoundPack(
            context, listOf(
                "sound/Punches/ghost_splash.wav",
            )
        )
    }
    val splash by pack {
        SoundPack(
            context, listOf(
                "sound/Punches/splash.wav",
            )
        )
    }
    val lightning by pack {
        SoundPack(
            context, listOf(
                "sound/Punches/lightning.wav",
            )
        )
    }

    val jetpack by prepare {
        context.resourcesVfs["sound/jetpack.wav"].readAudioClipEx()
    }
    val magicJetpack by prepare {
        context.resourcesVfs["sound/magic_jetpack.wav"].readAudioClipEx()
    }

    private fun String.wavName(): String = this.substringAfterLast("/").substringBefore(".wav")
    val concurrentClips = ConcurrentMutableMap<String, AudioClipEx>()

    private val preparations = listOf(
        "got",
        "5",
        "5_d",
        "6",
        "6_d",
        "7",
        "245",
        "246",
        "250",
        "251",
        "252",
        "253",
        "288",
        "289",
        "291",
        "295",
        "297",
        "299",
        "300",
        "301",
        "303",
        "306",
        "307",
        "310",
        "311",
        "314",
        "317",
        "319",
        "320",
        "323",
        "339",
        "342",
        "343",
        "347",
        "348",
        "358",
        "363",
        "364",
        "366",
        "367",
        "369",
        "370",
        "371",
        "379",
        "381",
        "383",
        "385",
        "386",
        "387",
        "390",
        "433",
        "438",
        "441",
        "445",
        "455",
        "456",
        "457",
        "459",
        "460",
        "461",
        "463",
        "468",
        "470",
        "471",
        "473",
        "507",
        "509",
    ).map { "sound/Misc/$it.wav" }.forEachIndexed { index, it ->
        val name = it.wavName()
        prepare(0) {
            val clip = context.resourcesVfs[it].readAudioClipEx()
            concurrentClips[name] = clip
            clip
        }
    }
    private val additionalPreparations = listOf(
        "Bomb Beep",
        "Door opened",
        "Elevator",
        "Generator Off",
        "Hacking",
        "Phone hang-up",
        "Phone pick-up",
        "Security Card in",
        "Security Card out",
    ).map { "sound/Additional/$it.wav" }.forEachIndexed { index, it ->
        val name = it.wavName()
        prepare(0) {
            val clip = context.resourcesVfs[it].readAudioClipEx()
            concurrentClips[name] = clip
            clip
        }
    }
}

class ExtraMusic(context: Context) : AssetPack(context) {

    private fun String.mp3name(): String = this.substringAfterLast("/").substringBefore(".mp3")
    val concurrentTracks = ConcurrentMutableMap<String, StreamBpm>()

    private val preparations = listOf(
        "music/magical girl 3d.mp3" to 150f,
        "music/mg safe area.mp3" to 50.9845f,
        "music/mg suspicious.mp3" to 121.45169f,
        "music/magical girl 1c 115.mp3" to 115f,
        "music/magical girl optimistic.mp3" to 134.98456f,
    ).forEachIndexed { index, it ->
        val name = it.first.mp3name()
        prepare(index) {
            val track =
                context.resourcesVfs[it.first].readAudioClipEx()
                    .asTrack(name, it.second, basicVolume = if (name == "magical girl optimistic") 0.075f else 0.1f, offset = 0.0f)
            concurrentTracks[name] = track
            track
        }
    }
}
