package vip.cdms.drsticker.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import vip.cdms.drsticker.data.repositories.StickerRepository
import vip.cdms.drsticker.ui.screens.StickerSetDetailRoute
import javax.inject.Inject

@HiltViewModel
class StickerSetDetailModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stickerRepository: StickerRepository,
): ViewModel() {
    val setId = savedStateHandle
        .toRoute<StickerSetDetailRoute>().setId
}
