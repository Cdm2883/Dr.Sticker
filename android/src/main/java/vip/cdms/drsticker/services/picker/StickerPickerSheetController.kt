package vip.cdms.drsticker.services.picker

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import vip.cdms.drsticker.data.SourceStickerResource
import vip.cdms.drsticker.data.StickerId
import vip.cdms.drsticker.data.StickerSetId
import vip.cdms.drsticker.ui.theme.AppTheme
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class StickerPickerSheetController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val modelProvider: Provider<StickerPickerSheetModel>,
) : AutoCloseable {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var sheetWindow: SheetWindow? = null

    private data class SheetWindow(
        val view: ComposeView,
        val owner: StickerPickerSheetOwner,
    )

    private val modelFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass == StickerPickerSheetModel::class.java)
            return modelProvider.get() as T
        }
    }

    fun show(
        onStickerSelected: (StickerSetId, StickerId, SourceStickerResource) -> Unit
    ) {
        check(Settings.canDrawOverlays(context)) { "Overlay permission is not granted." }
        hide()

        val owner = StickerPickerSheetOwner().apply { start() }
        val model = ViewModelProvider(owner, modelFactory)[StickerPickerSheetModel::class.java]
        model.open(
            onStickerSelected = onStickerSelected,
            onClose = ::hide,
        )
        val view = ComposeView(context).apply {
            setOnKeyListener { _, keyCode, event ->
                if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
                if (event.action == KeyEvent.ACTION_UP) {
                    owner.onBackPressedDispatcher.onBackPressed()
                }
                true
            }
            isFocusableInTouchMode = true
            requestFocus()
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeOnBackPressedDispatcherOwner(owner)
            setContent {
                AppTheme {
                    StickerPickerSheet(model)
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        try {
            windowManager.addView(view, params)
            sheetWindow = SheetWindow(view, owner)
        } catch (cause: Throwable) {
            view.disposeComposition()
            owner.destroy()
            throw cause
        }
    }

    fun hide() {
        val window = sheetWindow ?: return
        sheetWindow = null
        window.view.disposeComposition()
        if (window.view.isAttachedToWindow) windowManager.removeView(window.view)
        window.owner.destroy()
    }

    override fun close() = hide()
}
