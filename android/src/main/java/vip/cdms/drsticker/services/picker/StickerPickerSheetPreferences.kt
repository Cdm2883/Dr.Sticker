package vip.cdms.drsticker.services.picker

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StickerPickerSheetPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        const val PREFERENCES_NAME = "sticker_picker_sheet"
        const val KEY_GRID_ANCHOR = "grid_anchor"
        const val KEY_GRID_SET_ID = "grid_set_id"
        const val KEY_GRID_SCROLL_OFFSET = "grid_scroll_offset"
    }

    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    data class GridAnchor(
        val key: String,
        val setId: String,
        val scrollOffset: Int,
    )

    fun getGridAnchor(): GridAnchor? {
        return GridAnchor(
            key = preferences.getString(KEY_GRID_ANCHOR, null) ?: return null,
            setId = preferences.getString(KEY_GRID_SET_ID, null) ?: return null,
            scrollOffset = preferences.getInt(KEY_GRID_SCROLL_OFFSET, 0),
        )
    }

    fun setGridAnchor(anchor: GridAnchor) = preferences.edit {
        putString(KEY_GRID_ANCHOR, anchor.key)
            .putString(KEY_GRID_SET_ID, anchor.setId)
            .putInt(KEY_GRID_SCROLL_OFFSET, anchor.scrollOffset)
    }
}
