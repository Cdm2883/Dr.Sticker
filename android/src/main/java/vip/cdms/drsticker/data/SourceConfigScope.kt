package vip.cdms.drsticker.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

class SourceConfigValidationException(propertyName: String) :
    IllegalArgumentException("Invalid source configuration field: '$propertyName'.")

@Stable
open class ConfigState<T : Any> internal constructor(
    private val scope: SourceConfigScope<*>,
    val property: KProperty<*>,
    initialValue: T,
    private val validator: (T) -> Boolean,
) {
    var value: T by mutableStateOf(initialValue)
        private set

    val isValid: Boolean
        get() = validator(value)

    val showError: Boolean
        get() = scope.showValidationErrors && !isValid

    open fun update(value: T) {
        this.value = value
    }
}

@Stable
class EnvConfigState<T : Any> @PublishedApi internal constructor(
    scope: SourceConfigScope<*>,
    property: KProperty<*>,
    initialValue: T,
    sourceValue: SourceEnvConfigField<T>?,
    validator: (T) -> Boolean,
) : ConfigState<T>(
    scope = scope,
    property = property,
    initialValue = sourceValue?.value ?: initialValue,
    validator = validator,
) {
    private var environmentDraftValue: T = when (sourceValue) {
        is SourceEnvConfigField.Override -> sourceValue.environment
        is SourceEnvConfigField.Environment -> sourceValue.value
        null -> initialValue
    }

    var usesEnvironment by mutableStateOf(
        sourceValue !is SourceEnvConfigField.Override,
    )
        private set

    override fun update(value: T) {
        super.update(value)
        if (usesEnvironment) environmentDraftValue = value
    }

    fun updateUsesEnvironment(value: Boolean) {
        if (value == usesEnvironment) return
        usesEnvironment = value
        if (value) super.update(environmentDraftValue)
    }

    /**
     * [SourceEnvConfigField.Override.environment] is a form snapshot for mode switching only,
     * it must not update global environment storage.
     */
    val sourceValue: SourceEnvConfigField<T>
        get() = if (usesEnvironment) SourceEnvConfigField.Environment(value)
        else SourceEnvConfigField.Override(value, environmentDraftValue)
}

typealias SourceEnvValueProvider = (property: KProperty<*>) -> JsonElement?

@Stable
class SourceConfigScope<C : StickerSourceConfig> internal constructor(
    val from: C?,
    @PublishedApi internal val envProvider: SourceEnvValueProvider?,
) {
    private val fields = mutableStateListOf<ConfigState<*>>()
    private var submitFactory: (() -> C)? by mutableStateOf(null)

    internal var showValidationErrors by mutableStateOf(false)
        private set

    @Composable
    fun <T : Any> rememberConfigState(
        property: KProperty1<C, T>,
        initialValue: T,
        validator: (T) -> Boolean = { true },
    ): ConfigState<T> {
        val state = remember(this, property) {
            ConfigState(
                scope = this,
                property = property,
                initialValue = if (from != null) property.get(from) else initialValue,
                validator = validator,
            )
        }
        RegisterField(state)
        return state
    }

    @Composable
    inline fun <reified T : Any> rememberEnvConfigState(
        property: KProperty1<C, SourceEnvConfigField<T>>,
        initialValue: T,
        noinline validator: (T) -> Boolean = { true },
    ): EnvConfigState<T> {
        val sourceValue = if (from != null) property.get(from)
        else envProvider?.invoke(property)
            ?.let { Json.decodeFromJsonElement(serializer<T>(), it) }
            ?.let { SourceEnvConfigField.Environment(it) }
        val state = remember(this, property) {
            EnvConfigState(
                scope = this,
                property = property,
                initialValue = initialValue,
                sourceValue = sourceValue,
                validator = validator,
            )
        }
        RegisterField(state)
        return state
    }

    @Composable
    fun RegisterSubmit(factory: () -> C) {
        val currentFactory = rememberUpdatedState(factory)
        DisposableEffect(this) {
            submitFactory = { currentFactory.value() }
            onDispose { submitFactory = null }
        }
    }

    fun submit(): C {
        showValidationErrors = true
        val invalidField = fields.firstOrNull { !it.isValid }
        if (invalidField != null)
            throw SourceConfigValidationException(invalidField.property.name)
        return submitFactory?.invoke()
            ?: error("Source configuration form did not register RegisterSubmit().")
    }

    @PublishedApi
    @Composable
    internal fun RegisterField(state: ConfigState<*>) =
        DisposableEffect(this, state) {
            fields.add(state)
            onDispose { fields.remove(state) }
        }
}

@Composable
fun <C : StickerSourceConfig> rememberSourceConfigScope(
    from: C?,
    envProvider: SourceEnvValueProvider?,
): SourceConfigScope<C> = remember(from, envProvider) {
    SourceConfigScope(from, envProvider)
}
