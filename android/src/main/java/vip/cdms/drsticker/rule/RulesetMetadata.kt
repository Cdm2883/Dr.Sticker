package vip.cdms.drsticker.rule

import androidx.compose.runtime.Composable
import vip.cdms.drsticker.rule.adapters.RulesetAdapter
import vip.cdms.drsticker.rule.conditions.RulesetCondition
import vip.cdms.drsticker.rule.preprocess.RulesetPreprocess
import vip.cdms.drsticker.rule.triggers.RulesetTrigger

sealed interface RulesetMetadata<C> {
    val displayName: String
    val description: String

    fun createDefault(): C = throw NotImplementedError()

    @Composable
    fun Editor(config: C, onConfigChanged: (C) -> Unit): Unit = throw NotImplementedError()
}

interface RulesetConditionMetadata<C : RulesetCondition> : RulesetMetadata<C> {
    fun describe(config: C): String
}

interface RulesetTriggerMetadata<C : RulesetTrigger> : RulesetMetadata<C>

interface RulesetPreprocessMetadata<C : RulesetPreprocess> : RulesetMetadata<C> {
    val singleton: C? get() = null

    fun describe(config: C): String = description
}

interface RulesetAdapterMetadata<C : RulesetAdapter> : RulesetMetadata<C>

