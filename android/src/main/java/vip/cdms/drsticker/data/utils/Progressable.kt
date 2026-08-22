package vip.cdms.drsticker.data.utils

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@DslMarker
private annotation class ProgressableDsl

const val PROGRESSABLE_DYNAMIC_TOTAL = -1

data class ProgressableSnapshot(
    val fraction: Float,
    val labels: List<String?>,
)

@ProgressableDsl
class ProgressableScope internal constructor(
    private val session: ProgressableSession?,
    private val node: ProgressableNode?,
) {
    fun report(
        fraction: Float?,
        label: String? = null,
        key: String? = null,
    ) {
        if (fraction != null)
            require(fraction.isFinite() && fraction in 0f..1f) {
                "Progressable fraction must be finite and within 0f..1f: $fraction"
            }
        val session = session ?: return
        val node = node ?: return
        session.report(node, fraction, label, key)
    }

    companion object {
        internal val NoOp = ProgressableScope(null, null)
    }
}

class ProgressableController @Inject constructor() {
    private val controllerLock = Any()
    private val _state = MutableStateFlow<ProgressableSnapshot?>(null)
    private var activeSession: ProgressableSession? = null

    val state: StateFlow<ProgressableSnapshot?> = _state.asStateFlow()

    suspend fun <T> collect(block: suspend () -> T): T {
        val session = synchronized(controllerLock) {
            check(activeSession == null) {
                "ProgressableController.collect cannot be nested or concurrent."
            }
            ProgressableSession(::publish).also {
                activeSession = it
                _state.value = null
            }
        }

        return try {
            withContext(ProgressableContext(session, session.root)) {
                block()
            }
        } finally {
            session.close()
            synchronized(controllerLock) {
                if (activeSession === session) activeSession = null
            }
        }
    }

    fun reset() = synchronized(controllerLock) {
        _state.value = null
    }

    private fun publish(
        session: ProgressableSession,
        snapshot: ProgressableSnapshot,
    ) = synchronized(controllerLock) {
        if (activeSession === session) _state.value = snapshot
    }
}

suspend fun <T> progressable(
    total: Int = 1,
    block: suspend ProgressableScope.() -> T,
): T {
    require(total == PROGRESSABLE_DYNAMIC_TOTAL || total > 0) {
        "Progressable task total must be greater than zero or $PROGRESSABLE_DYNAMIC_TOTAL for dynamic total."
    }
    val context = currentCoroutineContext()[ProgressableContext]
        ?: return block(ProgressableScope.NoOp)
    if (!context.session.isOpen()) return block(ProgressableScope.NoOp)

    val node = context.session.createNode(context.node, total)
        ?: return block(ProgressableScope.NoOp)
    val scope = ProgressableScope(context.session, node)
    return withContext(ProgressableContext(context.session, node)) {
        val result = block(scope)
        context.session.complete(node)
        result
    }
}


internal class ProgressableContext(
    val session: ProgressableSession,
    val node: ProgressableNode,
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<ProgressableContext>
        get() = Key

    companion object Key : CoroutineContext.Key<ProgressableContext>
}

internal class ProgressableSession(
    private val publish: (ProgressableSession, ProgressableSnapshot) -> Unit,
) {
    private val lock = Any()
    val root = ProgressableNode(
        parent = null,
        total = PROGRESSABLE_DYNAMIC_TOTAL,
    )
    private var closed = false
    private var activeNode: ProgressableNode? = null

    fun isOpen() = synchronized(lock) { !closed }

    fun createNode(
        parent: ProgressableNode,
        total: Int,
    ) = synchronized(lock) {
        if (closed || parent.completed) return@synchronized null

        val node = ProgressableNode(parent, total)
        val slot = ProgressableSlot(parent)
        node.parentSlot = slot
        val oldParentFraction = parent.effectiveFraction()
        parent.addDirectSlot(slot)
        propagateFractionChange(parent, oldParentFraction)
        activeNode = node
        publishLocked()
        node
    }

    fun report(
        node: ProgressableNode,
        fraction: Float?,
        label: String?,
        key: String?,
    ) = synchronized(lock) {
        if (closed || node.completed) return

        val oldNodeFraction = node.effectiveFraction()
        val slot = when {
            key != null -> node.explicitSlot(key)
            fraction != null -> node.ownSlot()
            else -> null
        }
        propagateFractionChange(node, oldNodeFraction)
        if (fraction != null) updateSlot(checkNotNull(slot), fraction)
        if (label != null) {
            slot?.label = label
            node.label = label
        }
        activeNode = node
        publishLocked()
    }

    fun complete(node: ProgressableNode) = synchronized(lock) {
        if (closed || node.completed) return

        node.completed = true
        node.parentSlot?.let { updateSlot(it, 1f) }
        activeNode = node.parent
            ?.takeUnless { it.parent == null || it.label == null }
            ?: node
        publishLocked()
    }

    private fun propagateFractionChange(
        node: ProgressableNode,
        oldFraction: Float,
    ) {
        val newFraction = node.effectiveFraction()
        if (newFraction != oldFraction) {
            node.parentSlot?.let { updateSlot(it, newFraction) }
        }
    }

    fun close() = synchronized(lock) {
        closed = true
    }


    private fun updateSlot(slot: ProgressableSlot, fraction: Float) {
        val owner = slot.owner
        val oldOwnerFraction = owner.effectiveFraction()
        val oldFraction = slot.fraction
        if (oldFraction == fraction) return
        slot.fraction = fraction
        owner.directFraction += fraction - oldFraction
        val newOwnerFraction = owner.effectiveFraction()
        if (newOwnerFraction != oldOwnerFraction) {
            owner.parentSlot?.let { updateSlot(it, newOwnerFraction) }
        }
    }

    private fun publishLocked() {
        if (!closed) publish(this, snapshotLocked())
    }

    private fun snapshotLocked(): ProgressableSnapshot {
        val node = activeNode
            ?: return ProgressableSnapshot(root.effectiveFraction(), emptyList())
        val chain = ArrayList<ProgressableNode>(4)
        var current: ProgressableNode? = node
        while (current != null && current.parent != null) {
            chain += current
            current = current.parent
        }
        chain.reverse()
        return ProgressableSnapshot(
            fraction = root.effectiveFraction(),
            labels = chain.mapTo(ArrayList(chain.size)) { it.label },
        )
    }
}

internal class ProgressableNode(
    val parent: ProgressableNode?,
    val total: Int,
) {
    private val directSlots = ArrayList<ProgressableSlot>()
    private var explicitSlots: HashMap<String, ProgressableSlot>? = null
    private var ownSlot: ProgressableSlot? = null
    var parentSlot: ProgressableSlot? = null
    var directFraction = 0f
    var label: String? = null
    var completed = false

    fun ownSlot(): ProgressableSlot {
        ownSlot?.let { return it }
        return ProgressableSlot(this).also {
            ownSlot = it
            addDirectSlot(it)
        }
    }

    fun explicitSlot(key: String): ProgressableSlot {
        explicitSlots?.get(key)?.let { return it }
        val slot = ProgressableSlot(this)
        addDirectSlot(slot)
        (explicitSlots ?: HashMap<String, ProgressableSlot>().also { explicitSlots = it })[key] = slot
        return slot
    }

    fun addDirectSlot(slot: ProgressableSlot) {
        check(total == PROGRESSABLE_DYNAMIC_TOTAL || directSlots.size < total) {
            "Progressable task declared total=$total but produced ${directSlots.size + 1} keys."
        }
        directSlots += slot
    }

    fun effectiveFraction(): Float {
        if (completed) return 1f
        if (directSlots.isEmpty()) return ownSlot?.fraction ?: 0f
        val denominator = if (total == PROGRESSABLE_DYNAMIC_TOTAL) {
            directSlots.size
        } else {
            total
        }
        return (directFraction / denominator).coerceIn(0f, 1f)
    }
}

internal class ProgressableSlot(
    val owner: ProgressableNode,
) {
    var fraction = 0f
    var label: String? = null
}
