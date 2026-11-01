package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Stages a title's `E:\UDATA\<title id>` folder out of hakuX's private storage through the root
 * daemon on every read, and copies the staged tree back, owned by hakuX, after every restore.
 */
class XboxSaveHandler(
    private val context: Context,
    fal: FileAccessLayer,
    saveArchiver: SaveArchiver
) : FolderSaveHandler(context, fal, saveArchiver, PLATFORM_SLUG, TAG) {

    override fun resolveBasePath(config: SavePathConfig, basePathOverride: String?): String? =
        super.resolveBasePath(config, null)

    override fun findSaveFolderBySaveId(basePath: String, saveId: String): String? {
        val staged = stagingDir(saveId)
        return if (stage(basePath, saveId, staged)) staged.absolutePath else null
    }

    override fun findAllSaveFoldersBySaveId(basePath: String, saveId: String): List<String> =
        listOfNotNull(findSaveFolderBySaveId(basePath, saveId))

    override fun constructSavePath(baseDir: String, saveId: String): String =
        stagingDir(saveId).absolutePath

    override fun isCanonicalFolderPath(savePath: String, saveId: String): Boolean =
        savePath == stagingDir(saveId).absolutePath

    override fun isValidCachedSavePath(path: String): Boolean =
        File(path).parentFile?.name == STAGING_ROOT

    override suspend fun prepareForUpload(
        localPath: String,
        context: SaveContext
    ): PreparedSave? = withContext(Dispatchers.IO) {
        val saveId = context.saveId ?: return@withContext null
        val basePath = resolveBasePath(context.config, context.basePathOverride)
            ?: return@withContext null

        if (!stage(basePath, saveId, File(localPath))) {
            Logger.debug(TAG, "prepareForUpload: hakuX holds no save | saveId=$saveId")
            return@withContext null
        }
        super.prepareForUpload(localPath, context)
    }

    override fun unpackArchive(tempFile: File, targetFolder: File, saveId: String?): Boolean {
        if (!super.unpackArchive(tempFile, targetFolder, saveId)) return false
        val id = saveId ?: run {
            Logger.error(TAG, "unpackArchive: no save id, cannot place the save in hakuX")
            return false
        }
        return writeBack(targetFolder, id)
    }

    private fun stage(udataPath: String, saveId: String, destination: File): Boolean {
        destination.parentFile?.mkdirs()
        val script = """
            udata=${RootShell.quote(udataPath)}
            dst=${RootShell.quote(destination.absolutePath)}
            $FIND_TITLE_FUNCTION
            match=${'$'}(find_title "${'$'}udata" ${RootShell.quote(saveId)})
            rm -rf "${'$'}dst"
            [ -n "${'$'}match" ] || exit $EXIT_ABSENT
            cp -rp "${'$'}match" "${'$'}dst" || exit 1
            chown -R ${android.os.Process.myUid()}:${android.os.Process.myUid()} "${'$'}dst" || exit 1
            chmod -R u+rwX "${'$'}dst"
            restorecon -R "${'$'}dst" 2>/dev/null
            exit 0
        """.trimIndent()
        val result = RootShell.run(context, script)
        return when (result?.exitCode) {
            0 -> true
            EXIT_ABSENT -> false
            else -> {
                Logger.error(TAG, "stage failed | saveId=$saveId exit=${result?.exitCode} output=${result?.output}")
                false
            }
        }
    }

    private fun writeBack(staged: File, saveId: String): Boolean {
        val config = SavePathRegistry.getConfigForPlatform(EMULATOR_ID, PLATFORM_SLUG)
        val udataPath = config?.let { resolveBasePath(it, null) } ?: run {
            Logger.error(TAG, "writeBack: no hakuX save path configured")
            return false
        }
        val script = """
            src=${RootShell.quote(staged.absolutePath)}
            udata=${RootShell.quote(udataPath)}
            id=${RootShell.quote(saveId)}
            $FIND_TITLE_FUNCTION
            part="${'$'}(dirname "${'$'}udata")"
            [ -d "${'$'}part" ] || exit $EXIT_NO_DRIVE
            owner="${'$'}(stat -c %u:%g "${'$'}part")"
            if [ ! -d "${'$'}udata" ]; then
              mkdir "${'$'}udata" && chown "${'$'}owner" "${'$'}udata" && chmod 700 "${'$'}udata" || exit 1
            fi
            match=${'$'}(find_title "${'$'}udata" "${'$'}id")
            target="${'$'}{match:-${'$'}udata/${'$'}(echo "${'$'}id" | tr 'A-Z' 'a-z')}"
            tmp="${'$'}(dirname "${'$'}(dirname "${'$'}part")")/$RESTORE_TMP"
            rm -rf "${'$'}tmp"
            cp -rp "${'$'}src" "${'$'}tmp" || exit 1
            chown -R "${'$'}owner" "${'$'}tmp" || exit 1
            find "${'$'}tmp" -type d -exec chmod 700 {} +
            find "${'$'}tmp" -type f -exec chmod 600 {} +
            rm -rf "${'$'}target" && mv "${'$'}tmp" "${'$'}target" || exit 1
            restorecon -R "${'$'}target" 2>/dev/null
            exit 0
        """.trimIndent()
        val result = RootShell.run(context, script)
        val placed = result?.exitCode == 0
        if (placed) {
            Logger.info(TAG, "writeBack: placed the save in hakuX | saveId=$saveId")
        } else {
            Logger.error(
                TAG,
                "writeBack failed | saveId=$saveId exit=${result?.exitCode} " +
                    "rootAvailable=${RootShell.isAvailable} output=${result?.output}"
            )
        }
        return placed
    }

    private fun stagingDir(saveId: String): File =
        File(File(context.cacheDir, STAGING_ROOT), saveId)

    private companion object {
        const val PLATFORM_SLUG = "xbox"
        const val TAG = "XboxSaveHandler"
        const val STAGING_ROOT = "xbox-saves"
        const val RESTORE_TMP = "argosy-restore-tmp"
        const val EMULATOR_ID = "hakux"
        const val EXIT_ABSENT = 3
        const val EXIT_NO_DRIVE = 4
        val FIND_TITLE_FUNCTION = """
            find_title() {
              want=${'$'}(echo "${'$'}2" | tr 'a-z' 'A-Z')
              for f in "${'$'}1"/*; do
                [ -d "${'$'}f" ] || continue
                [ "${'$'}(basename "${'$'}f" | tr 'a-z' 'A-Z')" = "${'$'}want" ] && { echo "${'$'}f"; return; }
              done
            }
        """.trimIndent()
    }
}
