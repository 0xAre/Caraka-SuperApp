package com.example.caraka.network

/**
 * Central resource-management contract for the DTN layer.
 *
 * This object is the single source of truth for the quotas, lifetimes, retry caps, and drop
 * priorities mandated by the architecture baseline (Resource Management Model, decision D7).
 * It declares limits ONLY — it contains no behaviour. Execution units in Phase 1 (outbox retry,
 * EU-1.4) and Phase 2 (carry quota/TTL enforcement, EU-2.3) read these values so the policy is
 * defined once and never duplicated across call sites.
 *
 * Baseline references:
 *  - D1  expiry uses hop-count + relative monotonic time, NOT wall-clock as the gate.
 *  - D4  unicast retry is bounded (a few attempts, backoff + jitter), never infinite.
 *  - D7  store-carry-forward is bounded by a hard storage quota + max TTL + priority drop.
 */
object MeshPolicy {

    // ── Outbox storage quota (D7) ───────────────────────────────────────────────────────────────
    /** Hard cap on the number of pending transport units kept in the outbox at once. */
    const val OUTBOX_MAX_MESSAGES = 500
    /** Hard cap on the total payload bytes buffered in the outbox (≈2 MB). */
    const val OUTBOX_MAX_TOTAL_BYTES = 2L * 1024 * 1024

    // ── Message lifetime / expiry (D1) ──────────────────────────────────────────────────────────
    /**
     * Maximum wall-clock age (ms) a queued/carried message may reach before it is evicted as
     * EXPIRED. Used as a coarse safety bound only; the primary expiry gate is hop-count (TTL).
     * 24 hours matches the situational-message TTL guidance in the research.
     */
    const val MESSAGE_MAX_AGE_MS = 24L * 60 * 60 * 1000

    // ── Unicast retry (D4) ──────────────────────────────────────────────────────────────────────
    /** Maximum send attempts for an un-ACKed unicast message before it is marked FAILED. */
    const val UNICAST_MAX_ATTEMPTS = 4
    /** Base backoff (ms) for the first retry; subsequent attempts grow (e.g. exponential). */
    const val UNICAST_RETRY_BASE_MS = 10_000L
    /** Upper bound (ms) on a single backoff interval so retries never stall indefinitely. */
    const val UNICAST_RETRY_MAX_MS = 90_000L
    /** Random jitter (ms) added to each retry/flush to avoid synchronized retry storms. */
    const val RETRY_JITTER_MS = 3_000L

    // ── Queue-processor cadence (D10) ─────────────────────────────────────────────────────────────
    /** Period (ms) of the timer-based queue-processor tick that flushes the outbox. */
    const val QUEUE_PROCESSOR_TICK_MS = 15_000L
    /** Debounce window (ms) to ignore peer-appeared flapping before flushing to a peer again. */
    const val PEER_FLUSH_DEBOUNCE_MS = 8_000L

    // ── Drop priority (D7) ────────────────────────────────────────────────────────────────────────
    /**
     * Priority classes used to decide which messages survive when the outbox is over quota.
     * Higher ordinal = retained longer. Eviction removes the LOWEST priority, then the OLDEST,
     * then the MOST-replicated, in that order.
     */
    enum class DropPriority { STATUS, NORMAL, EMERGENCY }

    /** Map a wire `priority` string to a [DropPriority] class for eviction decisions. */
    fun dropPriorityOf(priority: String): DropPriority = when (priority.uppercase()) {
        "EMERGENCY" -> DropPriority.EMERGENCY
        "HIGH", "NORMAL" -> DropPriority.NORMAL
        else -> DropPriority.STATUS
    }
}
