package vip.cdms.drsticker.rule.conditions

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import kotlinx.serialization.Serializable
import vip.cdms.drsticker.rule.RulesetConditionMetadata
import vip.cdms.drsticker.services.ConditionContext
import javax.inject.Inject

@Serializable
class PackageNameCondition(
    val packageName: String = "",
) : RulesetCondition {
    override fun matches(context: ConditionContext) =
        context.packageName == packageName
}

class PackageNameConditionMetadata @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RulesetConditionMetadata<PackageNameCondition> {
    override val displayName get() = "Package name"
    override val description get() = "Match the current application package name."

    override fun createDefault() = PackageNameCondition()

    override fun describe(config: PackageNameCondition) =
    // context.getString(R.string.)
        // 应用包名是 <b>%1$s</b>
        "package name is <b>${config.packageName}</b>"

    @Composable
    override fun Editor(
        config: PackageNameCondition,
        onConfigChanged: (PackageNameCondition) -> Unit,
    ) {
        OutlinedTextField(
            value = config.packageName,
            onValueChange = {
                val filtered = it.replace("\n", "")
                onConfigChanged(PackageNameCondition(filtered))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Package name") },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {}
            )
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface PackageNameConditionModule {
    @Binds
    @IntoMap
    @ClassKey(PackageNameCondition::class)
    fun bindMetadata(metadata: PackageNameConditionMetadata): RulesetConditionMetadata<*>
}
