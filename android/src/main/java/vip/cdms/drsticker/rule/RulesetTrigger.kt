@file:Suppress("PackageDirectoryMismatch")

package vip.cdms.drsticker.rule.triggers

import kotlinx.serialization.Serializable

@Serializable
sealed interface RulesetTrigger

interface TriggerHandler<C : RulesetTrigger> {
    fun activate(config: C, onOpenPicker: () -> Unit): TriggerSession
}

fun interface TriggerSession : AutoCloseable {
    override fun close()
}
