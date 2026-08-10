package vip.cdms.drsticker.rule.conditions

import kotlinx.serialization.Serializable
import vip.cdms.drsticker.services.ConditionContext

@Serializable
class PackageNameCondition(
    val packageName: String,
) : RulesetCondition {
    override fun matches(context: ConditionContext) =
        context.packageName == packageName
}
