@file:Suppress("PackageDirectoryMismatch")

package vip.cdms.drsticker.rule.conditions

import kotlinx.serialization.Serializable
import vip.cdms.drsticker.services.ConditionContext

@Serializable
sealed interface RulesetCondition {
    fun matches(context: ConditionContext): Boolean
}

@Serializable
object Always : RulesetCondition {
    override fun matches(context: ConditionContext) = true
}

@Serializable
object Never : RulesetCondition {
    override fun matches(context: ConditionContext) = false
}

@Serializable
class Not(val child: RulesetCondition) : RulesetCondition {
    override fun matches(context: ConditionContext) = !child.matches(context)
}

@Serializable
class AllOf(val children: List<RulesetCondition>) : RulesetCondition {
    init {
        require(children.isNotEmpty()) { "AllOf requires at least one child." }
    }

    override fun matches(context: ConditionContext) = children.all { it.matches(context) }
}

@Serializable
class AnyOf(val children: List<RulesetCondition>) : RulesetCondition {
    init {
        require(children.isNotEmpty()) { "AnyOf requires at least one child." }
    }

    override fun matches(context: ConditionContext) = children.any { it.matches(context) }
}
