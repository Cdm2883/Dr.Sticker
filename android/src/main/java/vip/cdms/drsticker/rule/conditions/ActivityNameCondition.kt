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
import kotlinx.serialization.Transient
import vip.cdms.drsticker.rule.RulesetConditionMetadata
import vip.cdms.drsticker.services.ConditionContext
import javax.inject.Inject

@Serializable
class ActivityNameCondition(
    val activityName: String = "*",
) : RulesetCondition {
    @Transient
    val prefix =
        if (activityName.endsWith('*')) activityName.dropLast(1)
        else null

    override fun matches(context: ConditionContext): Boolean {
        val target = context.activityName ?: return false
        return if (prefix != null) {
            target.startsWith(prefix)
        } else {
            target == activityName
        }
    }
}

class ActivityNameConditionMetadata @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RulesetConditionMetadata<ActivityNameCondition> {
    override val displayName get() = "Activity name"
    override val description get() = "Match the current activity class name."

    override fun createDefault() = ActivityNameCondition()

    override fun describe(config: ActivityNameCondition) =
    // context.getString(R.string.)
        // 活动类名是 <b>%1$s</b>
        if (config.prefix == null) "activity name is <b>${config.activityName}</b>"
        // 活动类名以 <b>%1$s</b> 开头
        else "activity name starts with <b>${config.activityName}</b>"

    @Composable
    override fun Editor(
        config: ActivityNameCondition,
        onConfigChanged: (ActivityNameCondition) -> Unit,
    ) {
        OutlinedTextField(
            value = config.activityName,
            onValueChange = {
                val filtered = it.replace("\n", "")
                onConfigChanged(ActivityNameCondition(filtered))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Activity name") },
            supportingText = { Text("End with * to match a class name prefix.") },
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
interface ActivityNameConditionModule {
    @Binds
    @IntoMap
    @ClassKey(ActivityNameCondition::class)
    fun bindMetadata(metadata: ActivityNameConditionMetadata): RulesetConditionMetadata<*>
}
