@file:Suppress("PackageDirectoryMismatch")

package vip.cdms.drsticker.rule.adapters

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
sealed interface RulesetAdapter

interface AdapterHandler<C : RulesetAdapter> {
    suspend fun send(config: C, file: File)
}
