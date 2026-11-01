package com.nendo.argosy.debugtools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * Debug-only recap harness, `am broadcast -n <pkg>/.debugtools.RecapHarnessReceiver --es op <op>`.
 * Ops: `recap_preview` / `recap_preview_clear` with `game_id`; `monthly_preview` with optional
 * `month` (YYYY-MM, default last month) for the next Home render; `monthly_preview_clear`;
 * `seed_sessions` with `month` (YYYY-MM) writes debug play sessions; `seed_sessions_clear`.
 */
class RecapHarnessReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RecapHarnessEntryPoint {
        fun recapPreviewRequests(): RecapPreviewRequests
        fun monthlyRecapPreview(): MonthlyRecapPreview
        fun monthlySessionSeeder(): MonthlySessionSeeder
    }

    override fun onReceive(context: Context, intent: Intent) {
        val op = intent.getStringExtra("op") ?: return
        if (op !in KNOWN_OPS) {
            Log.w(TAG, "unknown op $op")
            return
        }
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            RecapHarnessEntryPoint::class.java
        )
        when (op) {
            OP_MONTHLY_PREVIEW, OP_MONTHLY_PREVIEW_CLEAR -> monthly(op, intent, entryPoint.monthlyRecapPreview())
            OP_SEED_SESSIONS, OP_SEED_SESSIONS_CLEAR -> seed(op, intent, entryPoint.monthlySessionSeeder())
            else -> preLaunch(op, intent, entryPoint.recapPreviewRequests())
        }
    }

    private fun seed(op: String, intent: Intent, seeder: MonthlySessionSeeder) {
        val month = intent.getStringExtra(EXTRA_MONTH)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        if (op == OP_SEED_SESSIONS && month == null) {
            Log.w(TAG, "seed_sessions needs month as YYYY-MM")
            return
        }
        runAsync("$op failed") {
            if (op == OP_SEED_SESSIONS_CLEAR) {
                seeder.clear()
                Log.i(TAG, "seeded sessions cleared")
            } else {
                Log.i(TAG, "seeded ${seeder.seed(month!!)} sessions into $month")
            }
        }
    }

    private fun preLaunch(op: String, intent: Intent, requests: RecapPreviewRequests) {
        val gameId = intent.gameIdExtra()
        if (op == OP_RECAP_PREVIEW && gameId == null) {
            Log.w(TAG, "recap_preview needs a numeric game_id")
            return
        }
        runAsync("recap_preview failed for game $gameId") {
            if (op == OP_RECAP_PREVIEW_CLEAR) {
                requests.clear(gameId)
                Log.i(TAG, "recap preview cleared for ${gameId?.let { "game $it" } ?: "every game"}")
            } else {
                requests.request(gameId!!)
                Log.i(TAG, "recap preview armed for every launch of game $gameId")
            }
        }
    }

    private fun monthly(op: String, intent: Intent, preview: MonthlyRecapPreview) {
        val monthText = intent.getStringExtra(EXTRA_MONTH)
        val month = monthText?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        if (op == OP_MONTHLY_PREVIEW && monthText != null && month == null) {
            Log.w(TAG, "monthly_preview needs month as YYYY-MM, got $monthText")
            return
        }
        runAsync("$op failed") {
            if (op == OP_MONTHLY_PREVIEW_CLEAR) {
                preview.clear()
                Log.i(TAG, "monthly preview cleared")
            } else {
                preview.request(month)
                Log.i(TAG, "monthly preview armed for ${month ?: "last month"} on the next Home render")
            }
        }
    }

    private fun runAsync(failure: String, block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, failure, e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun Intent.gameIdExtra(): Long? =
        getStringExtra(EXTRA_GAME_ID)?.toLongOrNull()
            ?: getLongExtra(EXTRA_GAME_ID, NO_GAME_ID).takeIf { it != NO_GAME_ID }

    private companion object {
        const val TAG = "RecapHarness"
        const val OP_RECAP_PREVIEW = "recap_preview"
        const val OP_RECAP_PREVIEW_CLEAR = "recap_preview_clear"
        const val OP_MONTHLY_PREVIEW = "monthly_preview"
        const val OP_MONTHLY_PREVIEW_CLEAR = "monthly_preview_clear"
        const val OP_SEED_SESSIONS = "seed_sessions"
        const val OP_SEED_SESSIONS_CLEAR = "seed_sessions_clear"
        val KNOWN_OPS = setOf(
            OP_RECAP_PREVIEW, OP_RECAP_PREVIEW_CLEAR, OP_MONTHLY_PREVIEW, OP_MONTHLY_PREVIEW_CLEAR,
            OP_SEED_SESSIONS, OP_SEED_SESSIONS_CLEAR
        )
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_MONTH = "month"
        const val NO_GAME_ID = -1L
    }
}
