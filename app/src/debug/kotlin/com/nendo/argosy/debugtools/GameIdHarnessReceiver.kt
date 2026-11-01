package com.nendo.argosy.debugtools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nendo.argosy.data.local.dao.GameDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only seam that reads or rewrites one game's title id, save id and lock, logged under
 * `GameIdHarness`.
 *
 * `am broadcast -n com.nendo.argosy.debug/com.nendo.argosy.debugtools.GameIdHarnessReceiver
 * --el gameId <id> [--es titleId <id> --es saveId <id> --ez locked <bool>]`
 */
class GameIdHarnessReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HarnessEntryPoint {
        fun gameDao(): GameDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val gameId = intent.getLongExtra("gameId", -1L).takeIf { it > 0 } ?: return
        val gameDao = EntryPointAccessors.fromApplication(
            context.applicationContext,
            HarnessEntryPoint::class.java
        ).gameDao()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (intent.hasExtra("saveId")) {
                    gameDao.setTitleAndSaveIdWithLock(
                        gameId,
                        intent.getStringExtra("titleId"),
                        intent.getStringExtra("saveId"),
                        intent.getBooleanExtra("locked", true)
                    )
                }
                val game = gameDao.getById(gameId)
                android.util.Log.i(
                    TAG,
                    "gameId=$gameId titleId=${game?.titleId} saveId=${game?.saveId} locked=${game?.titleIdLocked}"
                )
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GameIdHarness"
    }
}
