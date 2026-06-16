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

    // ── Hop limit / TTL per message class (D1) ──────────────────────────────────────────────────
    /**
     * Hop budget (TTL) per message class — the SINGLE SOURCE OF TRUTH. Call sites must NOT hardcode
     * TTL; they read these so the policy is tuned in one place.
     *
     * Tuned upward from the legacy 5/10 to extend multi-hop reach toward the long-range goal. This
     * is safe against storms: the per-message anti-replay dedup (in-memory `seenIds` + persistent
     * `messageExists`) makes every node forward a given id AT MOST ONCE, so total transmissions stay
     * O(nodes-in-range) no matter how large the TTL — a bigger TTL widens reach, it does NOT cause
     * exponential flooding. The delay-tolerant carry layer (Phase 2) is what ultimately bridges tens
     * of km; a generous hop budget simply maximises each opportunistic burst in between.
     */
    const val DEFAULT_TTL = 8
    /** Unicast TEXT — must survive a long relay chain to a distant recipient. */
    const val TTL_TEXT = 16
    /** SOS/EMERGENCY broadcast — the farthest-reaching class; life-safety, widest flood. */
    const val TTL_SOS = 32
    /** End-to-end ACK — must travel back roughly as far as the original message came. */
    const val TTL_ACK = 16
    /** Suspicious-content FLAG gossip — moderate reach; secondary feature. */
    const val TTL_FLAG = 12

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

    // ── Store-carry-forward / carry (D7 extended — aggressive carry with hard caps) ───────────────
    /**
     * Aggressive carry (user decision): EVERY message class (SOS, broadcast, transit unicast) may be
     * stored and re-broadcast to new contacts so it can ride MOVING carriers across partitions — the
     * only realistic way to bridge tens of km on phones (hop relay alone reaches ~1-2 km). Kept cheap
     * by HARD bounds so it never becomes a storage/battery DoS:
     *  - a per-bundle PAYLOAD SIZE CAP (small messages only — also nudges concise UX),
     *  - the existing outbox storage quota (bytes/count) with EMERGENCY evicted LAST,
     *  - a bounded re-broadcast count + coarse age expiry.
     */
    /** Bundles whose wire payload exceeds this are NOT carried (only best-effort flooded once). */
    const val MAX_CARRY_PAYLOAD_BYTES = 4 * 1024
    /** Minimum spacing between re-broadcasts of the SAME carried bundle. */
    const val CARRY_REBROADCAST_INTERVAL_MS = 60_000L
    /** After this many re-broadcasts a bundle goes dormant (kept until age/quota cleanup). */
    const val MAX_CARRY_REBROADCASTS = 90
    /** Max bundles re-broadcast in a single carry sweep (bounds per-tick work). */
    const val CARRY_BATCH_LIMIT = 64
    /** Global throttle on peer-appearance-triggered carry flushes. */
    const val CARRY_FLUSH_DEBOUNCE_MS = 12_000L
    /** UX guidance: keep composed message bodies under this so they always fit the carry cap. */
    const val CARRY_BODY_MAX_CHARS = 280

    // ── Probabilistic gossip forwarding (D12 — scale safeguard) ───────────────────────────────────
    /**
     * Pairs with the raised TTLs: when many neighbours are in range, having EVERY node re-flood a
     * NORMAL broadcast/transit message is wasteful and risks a storm. Above the density threshold a
     * node forwards only with a probability so the expected number of forwarders stays roughly
     * constant — fewer redundant transmissions without losing reach (dedup + carry back it up).
     * EMERGENCY/SOS is ALWAYS forwarded (life-safety) and never gossip-gated.
     */
    /** At or below this many direct neighbours, always forward (p = 1). */
    const val GOSSIP_DENSITY_THRESHOLD = 4
    /** Lower bound on forward probability even in a very dense cluster (never starve delivery). */
    const val GOSSIP_MIN_PROBABILITY = 0.35

    // ── Carry density adaptation ──────────────────────────────────────────────────────────────────
    /**
     * Re-broadcast carried bundles LESS often when many neighbours are around (dense cluster → the
     * message has likely already spread), and keep the aggressive base cadence when neighbours are
     * few (partition edge → carry is the only way forward). Slowdown grows ~1 step per this many
     * neighbours.
     */
    const val CARRY_DENSITY_REF = 6

    // ── Duty cycle (D14) ──────────────────────────────────────────────────────────────────────────
    /**
     * No mesh activity (no inbound/outbound traffic, no new peer) for this long → enter IDLE state
     * and widen the beacon/gossip/discovery intervals to save battery. Any activity returns to ACTIVE.
     */
    const val IDLE_THRESHOLD_MS = 60_000L
    /** Multiplier applied to beacon/gossip/discovery intervals while IDLE (e.g. 3s → 12s). */
    const val IDLE_INTERVAL_MULTIPLIER = 4L

    /**
     * After this much continued inactivity the node enters DEEP IDLE and suspends the most
     * power-hungry work — active Wi-Fi Direct discovery/scanning (EU-3.2). The passive LAN listener
     * stays alive so the node remains reachable and any inbound traffic wakes it back to ACTIVE.
     * NOTE: fully powering down Wi-Fi awaits a low-power presence channel (BLE), which is POSTPONED
     * (D15); until then we only suspend active scanning, never the receive path.
     */
    const val DEEP_IDLE_THRESHOLD_MS = 5L * 60 * 1000

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

    // ── Courier Mode (Caraka Kurir — Phase A+B) ───────────────────────────────────────────────
    /**
     * Batas ukuran payload bundle kurir — lebih besar dari carry epidemik (4KB) karena pengiriman
     * ini bersifat disengaja (bukan flood ke siapapun). Didesain agar mudah dinaikkan di masa depan.
     * Keputusan pengguna: 64KB untuk sekarang.
     */
    const val COURIER_MAX_PAYLOAD_BYTES = 64 * 1024  // 64 KB

    /** Umur maksimum bundle kurir sebelum expired dan dihapus dari storage B. */
    const val COURIER_BUNDLE_MAX_AGE_MS = 72L * 60 * 60 * 1000  // 72 jam

    /**
     * Timeout challenge-response saat Caraka Mode aktif (Stealth delivery).
     * Z harus mengirim COURIER_PROOF dalam waktu ini setelah menerima COURIER_CHALLENGE.
     */
    const val COURIER_CHALLENGE_TIMEOUT_MS = 30_000L  // 30 detik

    /**
     * TTL untuk COURIER_BROADCAST — pesan ini hanya boleh menjangkau peer di sekitar langsung B,
     * tidak boleh di-relay lebih jauh (local-only, 1 hop).
     */
    const val COURIER_BROADCAST_TTL = 1

    /**
     * Batas maksimum karakter konten dalam bundle kurir (guidance UX).
     * Lebih besar dari CARRY_BODY_MAX_CHARS (280) karena kurir adalah pengiriman disengaja.
     */
    const val COURIER_BODY_MAX_CHARS = 2000
}
