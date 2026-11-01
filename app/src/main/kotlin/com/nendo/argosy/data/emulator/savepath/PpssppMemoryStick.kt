package com.nendo.argosy.data.emulator.savepath

import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.storage.FileAccessLayer

/**
 * The memory stick standalone PPSSPP runs from, told apart from stale `PSP` trees by the config
 * file PPSSPP keeps in `<memstick>/PSP/SYSTEM`: `ppsspp.ini`, or `ppssppvr.ini` in VR builds.
 */
object PpssppMemoryStick {
    private val CONFIG_IDS = setOf("ppsspp", "ppsspp_gold")
    private val INI_NAMES = listOf("ppsspp.ini", "ppssppvr.ini")
    private const val SAVEDATA_SUFFIX = "/PSP/SAVEDATA"
    private const val SYSTEM_SUFFIX = "/PSP/SYSTEM"

    /**
     * The `PSP/SAVEDATA` folder among [candidates] whose memory stick holds a PPSSPP config file,
     * the newest config first and candidate order on a tie. Null for a layout that is not
     * standalone PPSSPP, and when no candidate's stick holds a config.
     */
    fun liveSaveData(config: SavePathConfig, candidates: List<String>, fal: FileAccessLayer): String? {
        if (config.emulatorId !in CONFIG_IDS) return null
        return candidates
            .map { it.trimEnd('/') }
            .distinct()
            .mapNotNull { saveData -> configModifiedAt(saveData, fal)?.let { saveData to it } }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun configModifiedAt(saveData: String, fal: FileAccessLayer): Long? {
        if (!saveData.endsWith(SAVEDATA_SUFFIX)) return null
        val system = saveData.removeSuffix(SAVEDATA_SUFFIX) + SYSTEM_SUFFIX
        return INI_NAMES
            .map { "$system/$it" }
            .filter { fal.exists(it) }
            .maxOfOrNull { fal.lastModified(it) }
    }
}
