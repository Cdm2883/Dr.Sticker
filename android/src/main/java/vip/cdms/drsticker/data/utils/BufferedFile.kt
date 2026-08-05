package vip.cdms.drsticker.data.utils

import kotlinx.coroutines.*
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.time.Duration

class BufferedFile<T>(
    private val file: File,
    private val defaultValue: () -> T,
    private val decode: (String) -> T,
    private val encode: (T) -> String,
    private val writeDelay: Duration,
) {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var value: T? = null
    private var writeJob: Job? = null
    private var version = 0L

    fun get(): T = synchronized(lock) {
        value ?: (file.takeIf { it.exists() }
            ?.readText()
            ?.let(decode)
            ?: defaultValue()).also { value = it }
    }

    fun update(transform: (T) -> T) = synchronized(lock) {
        value = transform(get())
        version++
        scheduleWrite()
    }

    private fun scheduleWrite() {
        if (writeJob?.isActive == true) return
        writeJob = scope.launch {
            delay(writeDelay)
            val (snapshot, snapshotVersion) = synchronized(lock) { get() to version }
            try {
                file.writeTextAtomically(encode(snapshot))
            } finally {
                synchronized(lock) {
                    writeJob = null
                    if (version != snapshotVersion) scheduleWrite()
                }
            }
        }
    }
}

fun File.writeTextAtomically(content: String) {
    parentFile?.mkdirs()
    val temporary = File(parentFile, ".$name.tmp")
    temporary.writeText(content)
    try {
        Files.move(
            temporary.toPath(),
            toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(
            temporary.toPath(),
            toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    } finally {
        temporary.delete()
    }
}
