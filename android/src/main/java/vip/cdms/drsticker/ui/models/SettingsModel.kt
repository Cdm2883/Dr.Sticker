package vip.cdms.drsticker.ui.models

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import vip.cdms.drsticker.data.repositories.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsModel @Inject constructor(
    val settingsRepository: SettingsRepository,
) : ViewModel() {
}
