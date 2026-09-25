package vip.cdms.drsticker.rule.adapters

import android.content.Context
import androidx.compose.runtime.Composable
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import kotlinx.serialization.Serializable
import vip.cdms.drsticker.rule.RulesetAdapterMetadata
import vip.cdms.drsticker.services.shizuku.ShizukuBridge
import javax.inject.Inject

@Serializable
data class ShizukuPasteAdapter(
    override val focusDelayMillis: Long = 0L,
    override val focusClickXExpression: String = $$"$screenWidth / 2",
    override val focusClickYExpression: String = $$"$screenHeight - 100",
    override val pasteDelayMillis: Long = 0L,
    override val postClickDelayMillis: Long = 300L,
    override val postClickXExpression: String = "",
    override val postClickYExpression: String = "",
    override val clickDurationMillis: Long = 1L,
    override val pastePath: Boolean = false,
) : BasePasteAdapter

class ShizukuPasteAdapterMetadata @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BasePasteAdapterMetadata<ShizukuPasteAdapter> {
    override val displayName get() = "Copy & Paste (Shizuku)"  // context.getString(R.string.)
    override val description get() = "Paste stickers with Shizuku input events."

    override fun createDefault() = ShizukuPasteAdapter()

    @Composable
    override fun Editor(
        config: ShizukuPasteAdapter,
        onConfigChanged: (ShizukuPasteAdapter) -> Unit,
    ) {
        @Suppress("DuplicatedCode")
        CommonEditor(
            config = config,
            onFocusDelayMillisChange = { onConfigChanged(config.copy(focusDelayMillis = it)) },
            onFocusClickXExpressionChange = { onConfigChanged(config.copy(focusClickXExpression = it)) },
            onFocusClickYExpressionChange = { onConfigChanged(config.copy(focusClickYExpression = it)) },
            onPasteDelayMillisChange = { onConfigChanged(config.copy(pasteDelayMillis = it)) },
            onPostClickDelayMillisChange = { onConfigChanged(config.copy(postClickDelayMillis = it)) },
            onPostClickXExpressionChange = { onConfigChanged(config.copy(postClickXExpression = it)) },
            onPostClickYExpressionChange = { onConfigChanged(config.copy(postClickYExpression = it)) },
            onClickDurationMillisChange = { onConfigChanged(config.copy(clickDurationMillis = it)) },
            onPastePathChange = { onConfigChanged(config.copy(pastePath = it)) },
        )
    }
}

class ShizukuPasteAdapterHandler @Inject constructor(
    @ApplicationContext context: Context,
    private val shizukuBridge: ShizukuBridge,
) : BasePasteAdapterHandler<ShizukuPasteAdapter>(context) {
    override suspend fun click(x: Int, y: Int, durationMillis: Long) =
        check(shizukuBridge.swipe(x, y, x, y, durationMillis))
        { "Shizuku click failed or is unavailable." }

    override suspend fun performPaste() =
        check(shizukuBridge.pasteClipboard())
        { "Shizuku paste failed or is unavailable." }
}

@Module
@InstallIn(SingletonComponent::class)
interface ShizukuPasteAdapterModule {
    @Binds
    @IntoMap
    @ClassKey(ShizukuPasteAdapter::class)
    fun bindMetadata(metadata: ShizukuPasteAdapterMetadata): RulesetAdapterMetadata<*>

    @Binds
    @IntoMap
    @ClassKey(ShizukuPasteAdapter::class)
    fun bindHandler(handler: ShizukuPasteAdapterHandler): AdapterHandler<*>
}
