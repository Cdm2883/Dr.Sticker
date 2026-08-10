package vip.cdms.drsticker.rule.conditions

import kotlinx.serialization.Serializable
import vip.cdms.drsticker.services.ConditionContext

@Serializable
class ActivityNameCondition(
    val activityName: String,
) : RulesetCondition {
    override fun matches(context: ConditionContext) =
        context.activityName == activityName
}
