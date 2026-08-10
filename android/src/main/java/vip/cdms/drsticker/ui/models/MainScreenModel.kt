package vip.cdms.drsticker.ui.models

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import vip.cdms.drsticker.services.StickerServiceController
import vip.cdms.drsticker.services.StickerServiceState
import vip.cdms.drsticker.services.picker.StickerPickerSheetController
import javax.inject.Inject

@HiltViewModel
class MainScreenModel @Inject constructor(
    private val stickerServiceController: StickerServiceController,
    private val stickerPickerSheetController: StickerPickerSheetController,
): ViewModel() {
    val serviceState = stickerServiceController.state

    fun toggleStickerService() {
        when (serviceState.value) {
            StickerServiceState.Stopped,
            is StickerServiceState.Failed ->
                stickerServiceController.start()

            StickerServiceState.Running ->
                stickerServiceController.stop()

            StickerServiceState.Starting,
            StickerServiceState.Stopping -> Unit
        }
    }

    fun openPickerSheet() =
        stickerPickerSheetController.show(onStickerSelected = { _, _, _ -> })
}
