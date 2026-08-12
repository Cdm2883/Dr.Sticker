package vip.cdms.drsticker.services.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku
import vip.cdms.drsticker.services.ConditionContext
import vip.cdms.drsticker.services.utils.hasShizukuPermission
import vip.cdms.drsticker.services.utils.isShizukuInstalled
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuBridge @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val conditionContext: ConditionContext,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var conditionConsumer: ((ConditionContext) -> Unit)? = null
    private var activeSwipes = 0
    private var service: IShizukuUserService? = null
    private var serviceArgs: Shizuku.UserServiceArgs? = null
    private var connection: ServiceConnection? = null
    private var binding: CompletableDeferred<IShizukuUserService?>? = null

    private val conditionCallback = object : IShizukuConditionCallback.Stub() {
        override fun onTopTaskChanged(packageName: String?, activityName: String?) {
            scope.launch {
                val changed = if (packageName.isNullOrBlank()) {
                    if (conditionContext.packageName == null && conditionContext.activityName == null) {
                        false
                    } else {
                        conditionContext.reset()
                        true
                    }
                } else {
                    conditionContext.update(packageName, activityName?.ifBlank { null })
                }
                if (changed) conditionConsumer?.invoke(conditionContext)
            }
        }
    }

    fun isAvailable() =
        isShizukuInstalled(context) && hasShizukuPermission()

    fun setConditionConsumer(consumer: ((ConditionContext) -> Unit)?) {
        conditionConsumer = consumer
        applyDemand()
    }

    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMillis: Long,
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        activeSwipes++
        try {
            val service = getService() ?: return@withContext false
            withContext(Dispatchers.Default) {
                service.swipe(startX, startY, endX, endY, durationMillis)
            }
        } catch (cause: Throwable) {
            Log.w(TAG, "Shizuku swipe failed.", cause)
            false
        } finally {
            activeSwipes--
            applyDemand()
        }
    }


    private fun applyDemand() {
        if (conditionConsumer != null) {
            scope.launch {
                val remote = getService() ?: return@launch
                if (conditionConsumer != null && !remote.registerConditionListener(conditionCallback)) {
                    Log.w(TAG, "Failed to register the Shizuku task-stack listener.")
                }
            }
        } else if (activeSwipes == 0) {
            unbind()
        } else {
            service?.runCatching { unregisterConditionListener() }
        }
    }

    private suspend fun getService(): IShizukuUserService? {
        service?.let { return it }
        binding?.let { return it.await() }
        if (!isAvailable()) return null

        val result = CompletableDeferred<IShizukuUserService?>()
        binding = result
        val args = Shizuku.UserServiceArgs(
            ComponentName(context, ShizukuUserService::class.java),
        ).daemon(false)
            .processNameSuffix("shizuku")
            .version(2)
        val newConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (connection !== this) return
                service = IShizukuUserService.Stub.asInterface(binder)
                result.complete(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (connection !== this) return
                service = null
                serviceArgs = null
                connection = null
                result.complete(null)
            }
        }
        serviceArgs = args
        connection = newConnection
        try {
            Shizuku.bindUserService(args, newConnection)
        } catch (cause: Throwable) {
            Log.w(TAG, "Failed to bind Shizuku user service.", cause)
            connection = null
            serviceArgs = null
            result.complete(null)
        }
        return try {
            result.await()
        } finally {
            if (binding === result) binding = null
        }
    }

    private fun unbind() {
        service?.runCatching { unregisterConditionListener() }
        service = null
        binding?.complete(null)
        binding = null
        val args = serviceArgs
        val currentConnection = connection
        serviceArgs = null
        connection = null
        if (args != null && currentConnection != null) {
            runCatching { Shizuku.unbindUserService(args, currentConnection, true) }
        }
    }

    private companion object {
        const val TAG = "ShizukuBridge"
    }
}
