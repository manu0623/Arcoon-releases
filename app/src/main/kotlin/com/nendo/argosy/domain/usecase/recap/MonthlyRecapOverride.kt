package com.nendo.argosy.domain.usecase.recap

import java.time.YearMonth

/**
 * A pending request to show the monthly recap on the next Home render, regardless of the setting
 * and the shown-once rule. [take] hands back the requested month at most once per request, or
 * null when nothing is pending.
 */
interface MonthlyRecapOverride {
    suspend fun take(): YearMonth?
}
