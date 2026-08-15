package vip.cdms.drsticker.rule.preprocess

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import kotlinx.serialization.Serializable
import vip.cdms.drsticker.rule.RulesetPreprocessMetadata
import javax.inject.Inject

@Serializable
data class ExtensionFilter(
    val extensions: String = "",
    val passOnMatch: Boolean = false,
) : RulesetPreprocess

class ExtensionFilterMetadata @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RulesetPreprocessMetadata<ExtensionFilter> {
    override val displayName = "Extension filter"
    override val description = "Continue or stop preprocessing based on the file extension."

    override fun describe(config: ExtensionFilter): String {
        val extensions = config.extensions.ifBlank { "*" }
        return if (config.passOnMatch) {
            // context.getString(R.string.)
            // 扩展名匹配 <b>%1$s</b> 时继续后续处理
            "Continue preprocessing when extension matches <b>$extensions</b>."
        } else {
            // context.getString(R.string.)
            // 扩展名匹配 <b>%1$s</b> 时停止后续处理
            "Stop preprocessing when extension matches <b>$extensions</b>."
        }
    }

    override fun createDefault() = ExtensionFilter()

    @Composable
    override fun Editor(
        config: ExtensionFilter,
        onConfigChanged: (ExtensionFilter) -> Unit,
    ) {
        OutlinedTextField(
            value = config.extensions,
            onValueChange = { onConfigChanged(config.copy(extensions = it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Extensions") },
            supportingText = { Text("Separate extensions with commas") },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = config.passOnMatch,
                onCheckedChange = {
                    onConfigChanged(config.copy(passOnMatch = it))
                },
            )
            Text(
                if (config.passOnMatch) "Continue on match" else "Stop on match",
            )
        }
    }
}

class ExtensionFilterHandler @Inject constructor() : PreprocessHandler<ExtensionFilter> {
    override suspend fun process(
        config: ExtensionFilter,
        input: ProcessingSticker,
    ): ProcessingSticker? {
        val matches = config.extensions
            .splitToSequence(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .any { extension -> extension.equals(input.extension, ignoreCase = true) }
        val shouldContinue = matches == config.passOnMatch
        return input.takeIf { shouldContinue }
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface ExtensionFilterModule {
    @Binds
    @IntoMap
    @ClassKey(ExtensionFilter::class)
    fun bindMetadata(metadata: ExtensionFilterMetadata): RulesetPreprocessMetadata<*>

    @Binds
    @IntoMap
    @ClassKey(ExtensionFilter::class)
    fun bindHandler(handler: ExtensionFilterHandler): PreprocessHandler<*>
}

