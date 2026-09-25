package vip.cdms.drsticker.data.repositories

import vip.cdms.drsticker.data.injection.PreferencesStoreProvider
import vip.cdms.drsticker.data.utils.boolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    preferencesStoreProvider: PreferencesStoreProvider,
) {
    private val preferences = preferencesStoreProvider.get("app_settings")

    val preferAccessibilityService =
        preferences.boolean("prefer_accessibility_service", false)

    val showPickerFromTop =
        preferences.boolean("show_picker_from_top", false)

    val keepPickerOpen =
        preferences.boolean("keep_picker_open", false)
}
