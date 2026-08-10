package vip.cdms.drsticker.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import vip.cdms.drsticker.ui.screens.RulesetDetailRoute
import javax.inject.Inject

@HiltViewModel
class RulesetDetailModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val rulesetId = savedStateHandle
        .toRoute<RulesetDetailRoute>().rulesetId
}
