package vip.cdms.drsticker.services

import android.accessibilityservice.GestureDescription
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityBridge @Inject constructor() {
    private var service: AccessibilityService? = null
    private var conditionConsumer: ((ConditionContext) -> Unit)? = null

    fun setConditionConsumer(consumer: ((ConditionContext) -> Unit)?) {
        conditionConsumer = consumer
        service?.updateEventDemand(consumer != null)
    }

    internal fun connect(service: AccessibilityService) {
        this.service = service
        service.updateEventDemand(conditionConsumer != null)
    }

    internal fun disconnect(service: AccessibilityService) {
        if (this.service === service) this.service = null
    }

    internal fun emitConditionContext(event: ConditionContext) {
        conditionConsumer?.invoke(event)
    }


    sealed interface GestureResult {
        object Completed : GestureResult
        object Cancelled : GestureResult
        object Unavailable : GestureResult
    }

    suspend fun dispatchGesture(
        stroke: GestureDescription.StrokeDescription,
    ): GestureResult {
        val connectedService = service ?: return GestureResult.Unavailable
        return suspendCancellableCoroutine { continuation ->
            connectedService.dispatch(stroke, continuation)
        }
    }
}
