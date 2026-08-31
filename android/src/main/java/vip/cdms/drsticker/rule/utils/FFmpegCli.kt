package vip.cdms.drsticker.rule.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object FFmpegCli {
    private external fun run0(vararg args: String): Int

    init {
        System.loadLibrary("drsticker")
    }

    private val mutex = Mutex()
    suspend fun run(vararg args: String) =
        withContext(Dispatchers.IO) { mutex.withLock { run0(*args) } }
}
