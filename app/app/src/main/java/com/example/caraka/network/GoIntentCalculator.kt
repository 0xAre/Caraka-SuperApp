package com.example.caraka.network

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * GoIntentCalculator — Smart Group Owner Election for WiFi Direct.
 *
 * WiFi Direct uses a `groupOwnerIntent` value (0–15) during GO negotiation:
 *   - 15 = strongly prefers to be GO (server role)
 *   -  0 = strongly prefers to be Client
 *   - The device with the higher intent wins the GO role
 *
 * Problem with hardcoded 15: any device can become GO regardless of its battery
 * state or load. If a low-battery device becomes GO and leaves the group,
 * ALL peers in that group are disconnected (star topology collapse).
 *
 * This calculator scores each connect attempt to assign a smart intent value:
 *
 *   score = (batteryPct * 0.40)         ← battery health
 *         + (isAuthority  * 0.30 * 15)  ← authority devices are preferred GOs
 *         + ((1/relayLoad) * 0.30 * 15) ← less loaded devices preferred
 *
 * Score is clamped to [1, 15] so we always participate in negotiation.
 *
 * Authority role bonus ensures BPBD/Polri/PMI devices become GOs when present,
 * because authority devices are assumed to have better power and connectivity.
 */
object GoIntentCalculator {

    private const val TAG = "GoIntent"

    // Thresholds
    private const val LOW_BATTERY_THRESHOLD = 20    // % — if below, stay as client
    private const val CRITICAL_BATTERY_THRESHOLD = 10 // % — never become GO

    /**
     * Calculate the groupOwnerIntent value for the next connect attempt.
     *
     * @param context Android context (for battery status)
     * @param isAuthority Whether the current user has an authority role (BPBD/Polri/PMI)
     * @param currentRelayLoad Current messages-relayed-per-minute rate (from WifiDirectManager)
     * @return groupOwnerIntent in range [0, 15]
     */
    fun calculate(
        context: Context,
        isAuthority: Boolean,
        currentRelayLoad: Int
    ): Int {
        val batteryPct = getBatteryLevel(context)
        val intent = computeIntent(batteryPct, isAuthority, currentRelayLoad)
        Log.d(TAG, "GO intent=$intent [battery=$batteryPct%, authority=$isAuthority, relayLoad=$currentRelayLoad]")
        return intent
    }

    private fun computeIntent(
        batteryPct: Int,
        isAuthority: Boolean,
        relayLoad: Int
    ): Int {
        // Critical battery — avoid GO entirely to preserve power for messaging
        if (batteryPct <= CRITICAL_BATTERY_THRESHOLD) {
            Log.w(TAG, "Battery critical ($batteryPct%) — intent=0 (never GO)")
            return 0
        }

        // Low battery — strongly prefer client role
        if (batteryPct <= LOW_BATTERY_THRESHOLD) {
            return if (isAuthority) 6 else 2  // Authority may still take GO if no alternative
        }

        // Compute normalized score components [0.0 .. 1.0]
        val batteryScore = batteryPct / 100.0

        // Authority bonus: BPBD/Polri/PMI have better infrastructure → prefer as GO
        val authorityScore = if (isAuthority) 1.0 else 0.0

        // Load score: inverse of relay load (busier relay = lower priority for GO)
        // relayLoad=0 → 1.0, relayLoad=10 → 0.5, relayLoad=100 → ~0.0
        val loadScore = 1.0 / (1.0 + (relayLoad / 10.0))

        // Weighted composite [0.0..1.0]
        val composite = (batteryScore * 0.40) +
                        (authorityScore * 0.35) +
                        (loadScore * 0.25)

        // Map to [1, 15] — always participate, never abstain
        val intent = (composite * 15.0).toInt().coerceIn(1, 15)
        return intent
    }

    /**
     * Read the current battery percentage via a sticky broadcast.
     * Lightweight — no receiver registration needed.
     */
    fun getBatteryLevel(context: Context): Int {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level == -1 || scale == -1 || scale == 0) 80 // Assume 80% if unreadable
            else ((level.toFloat() / scale.toFloat()) * 100).toInt()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read battery level: ${e.message}")
            80 // Safe default — assume OK battery
        }
    }

    /**
     * Returns a human-readable explanation of the current GO intent.
     * Useful for the Command Dashboard / debug overlay.
     */
    fun describe(intent: Int, batteryPct: Int, isAuthority: Boolean): String {
        return when {
            intent == 0 -> "⚡ Battery kritis ($batteryPct%) — mode client saja"
            intent <= 4 -> "🔋 Battery rendah ($batteryPct%) — prefer client"
            intent <= 8 -> "⚖️ Intent sedang ($intent/15) — negosiasi normal"
            isAuthority && intent > 12 -> "🛡️ Authority device — sangat prefer jadi GO ($intent/15)"
            else -> "✅ Intent tinggi ($intent/15, battery $batteryPct%) — prefer GO"
        }
    }
}
