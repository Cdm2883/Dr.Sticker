package vip.cdms.drsticker.data.utils

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class WriteMode { APPLY, COMMIT }

class PreferencesStore internal constructor(
    private val preferences: SharedPreferences,
) {
    private val states = ConcurrentHashMap<String, MutableStateFlow<Any?>>()

    internal fun <T> create(
        key: String,
        default: T,
        read: (SharedPreferences, String, T) -> T,
        write: (SharedPreferences.Editor, String, T) -> Unit,
        writeMode: WriteMode,
    ): PreferenceProperty<T> {
        @Suppress("UNCHECKED_CAST")
        val state = states.computeIfAbsent(key)
        { MutableStateFlow(read(preferences, key, default)) } as MutableStateFlow<T>
        return PreferenceProperty(state) { value ->
            val editor = preferences.edit()
            write(editor, key, value)
            when (writeMode) {
                WriteMode.APPLY -> editor.apply()
                WriteMode.COMMIT -> check(editor.commit()) { "Failed to persist preference \"$key\"." }
            }
        }
    }
}

class PreferenceProperty<T> internal constructor(
    private val state: MutableStateFlow<T>,
    private val write: (T) -> Unit,
) : ReadWriteProperty<Any?, T> {
    val flow = state.asStateFlow()
    var value
        get() = state.value
        set(value) {
            write(value)
            state.value = value
        }

    override fun getValue(thisRef: Any?, property: KProperty<*>) = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}

@Composable
fun <T> PreferenceProperty<T>.state(): MutableState<T> {
    val readState = flow.collectAsStateWithLifecycle()
    return remember(readState) {
        object : MutableState<T> {
            override var value: T
                get() = readState.value
                set(value) {
                    this@state.value = value
                }

            override fun component1(): T = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

fun PreferencesStore.boolean(
    key: String,
    default: Boolean,
    writeMode: WriteMode = WriteMode.APPLY,
) = create(
    key = key,
    default = default,
    read = { p, k, d -> p.getBoolean(k, d) },
    write = { e, k, v -> e.putBoolean(k, v) },
    writeMode = writeMode,
)

fun PreferencesStore.int(
    key: String,
    default: Int,
    writeMode: WriteMode = WriteMode.APPLY,
) = create(
    key = key,
    default = default,
    read = { p, k, d -> p.getInt(k, d) },
    write = { e, k, v -> e.putInt(k, v) },
    writeMode = writeMode,
)

fun PreferencesStore.float(
    key: String,
    default: Float,
    writeMode: WriteMode = WriteMode.APPLY,
) = create(
    key = key,
    default = default,
    read = { p, k, d -> p.getFloat(k, d) },
    write = { e, k, v -> e.putFloat(k, v) },
    writeMode = writeMode,
)

fun PreferencesStore.string(
    key: String,
    default: String? = null,
    writeMode: WriteMode = WriteMode.APPLY,
) = create(
    key = key,
    default = default,
    read = { p, k, d -> p.getString(k, d) },
    write = { e, k, v -> if (v == null) e.remove(k) else e.putString(k, v) },
    writeMode = writeMode,
)

fun <T : Enum<T>> PreferencesStore.enum(
    key: String,
    default: T,
    writeMode: WriteMode = WriteMode.APPLY,
): PreferenceProperty<T> {
    val defaultClass = default.declaringJavaClass
    val entries = requireNotNull(defaultClass.enumConstants) { "Failed to read enum entries of $defaultClass." }
    return create(
        key = key,
        default = default,
        read = { p, k, d ->
            val name = p.getString(k, null)
            entries.firstOrNull { it.name == name } ?: d
        },
        write = { e, k, v -> e.putString(k, v.name) },
        writeMode = writeMode,
    )
}

inline fun <reified T> PreferencesStore.serialized(
    key: String,
    default: T,
    json: Json,
    writeMode: WriteMode = WriteMode.APPLY,
) = serialized(
    key = key,
    default = default,
    json = json,
    serializer = json.serializersModule.serializer(),
    writeMode = writeMode,
)

fun <T> PreferencesStore.serialized(
    key: String,
    default: T,
    json: Json,
    serializer: KSerializer<T>,
    writeMode: WriteMode = WriteMode.APPLY,
) = create(
    key = key,
    default = default,
    read = { p, k, d ->
        p.getString(k, null)
            ?.let { json.decodeFromString(serializer, it) } ?: d
    },
    write = { e, k, v ->
        e.putString(k, json.encodeToString(serializer, v))
    },
    writeMode = writeMode,
)
