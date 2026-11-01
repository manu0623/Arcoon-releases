package com.nendo.argosy.debugtools

import android.content.Context
import com.nendo.argosy.domain.usecase.recap.MonthlyRecapOverride
import com.nendo.argosy.domain.usecase.recap.RecapEligibility
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-only request to show the monthly recap on the next Home render. Holds one month, or the
 * month before the current one when armed without a month. Persisted in its own preferences file.
 */
@Singleton
class MonthlyRecapPreview @Inject constructor(
    @ApplicationContext private val context: Context
) : MonthlyRecapOverride {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    suspend fun request(month: YearMonth?): Boolean = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_MONTH, month?.toString() ?: LAST_MONTH).commit()
    }

    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_MONTH).commit()
    }

    override suspend fun take(): YearMonth? = withContext(Dispatchers.IO) {
        synchronized(this@MonthlyRecapPreview) {
            val stored = prefs.getString(KEY_MONTH, null) ?: return@synchronized null
            prefs.edit().remove(KEY_MONTH).commit()
            resolve(stored, Instant.now(), ZoneId.systemDefault())
        }
    }

    companion object {
        private const val PREFS_NAME = "debug_monthly_recap_preview"
        private const val KEY_MONTH = "month"
        private const val LAST_MONTH = "last"

        fun resolve(stored: String, now: Instant, zone: ZoneId): YearMonth? =
            if (stored == LAST_MONTH) RecapEligibility.digestMonthAt(now, zone) else runCatching { YearMonth.parse(stored) }.getOrNull()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MonthlyRecapPreviewModule {
    @Binds
    @IntoSet
    abstract fun bindMonthlyRecapPreview(preview: MonthlyRecapPreview): MonthlyRecapOverride
}
