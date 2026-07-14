package io.legado.app.data.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.legado.app.constant.PreferKey
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryCompatibilityTest {

    @Test
    fun normalizeLegacyBrightnessVisibilityBoolean() {
        assertEquals("1", normalizeLegacyPreferenceValue(PreferKey.showBrightnessView, true))
        assertEquals("0", normalizeLegacyPreferenceValue(PreferKey.showBrightnessView, false))
        assertEquals("2", normalizeLegacyPreferenceValue(PreferKey.showBrightnessView, "2"))
    }

    @Test
    fun readLegacyBrightnessVisibilityBooleanAsString() {
        val enabled = mutablePreferencesOf(
            booleanPreferencesKey(PreferKey.showBrightnessView) to true
        )
        val disabled = mutablePreferencesOf(
            booleanPreferencesKey(PreferKey.showBrightnessView) to false
        )
        val vertical = mutablePreferencesOf(
            stringPreferencesKey(PreferKey.showBrightnessView) to "2"
        )

        assertEquals("1", enabled.getStringCompat(PreferKey.showBrightnessView, "0"))
        assertEquals("0", disabled.getStringCompat(PreferKey.showBrightnessView, "1"))
        assertEquals("2", vertical.getStringCompat(PreferKey.showBrightnessView, "1"))
    }
}
