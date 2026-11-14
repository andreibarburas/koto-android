package com.brbrs.koto.ui.theme

import com.brbrs.koto.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class DisplayPreferences(
    /** "small", "default", "large", or "extra_large". */
    val textSize: String = "default",
    /** DM Serif Display + Inter Tight pairing (default) vs system fonts. */
    val useCustomFont: Boolean = true,
)

fun textSizeMultiplier(textSize: String): Float = when (textSize) {
    "small"       -> 0.9f
    "large"       -> 1.15f
    "extra_large" -> 1.3f
    else          -> 1.0f
}

/** Thin facade over [SettingsRepository] -- see its kdoc. Kept so screens don't reach past it. */
@Singleton
class DisplayPreferencesRepository @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    val preferences: Flow<DisplayPreferences> = settingsRepository.settings.map { s ->
        DisplayPreferences(textSize = s.textSize, useCustomFont = s.useCustomFont)
    }

    fun setTextSize(size: String) = settingsRepository.setTextSize(size)
    fun setUseCustomFont(enabled: Boolean) = settingsRepository.setUseCustomFont(enabled)
}
