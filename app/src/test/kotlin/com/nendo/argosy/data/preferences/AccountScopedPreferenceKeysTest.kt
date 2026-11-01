package com.nendo.argosy.data.preferences

import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountScopedPreferenceKeysTest {

    @Test
    fun `both library sync times are kept per account`() {
        assertTrue(AccountScopedPreferenceKeys.isPerAccount(stringPreferencesKey("last_romm_sync")))
        assertTrue(AccountScopedPreferenceKeys.isPerAccount(stringPreferencesKey("romm_changes_watermark")))
    }
}
