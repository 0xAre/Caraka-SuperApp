package com.example.caraka.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.caraka.CarakaApp
import com.example.caraka.MainActivity
import com.example.caraka.R
import com.example.caraka.network.MeshPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service yang menjaga mesh tetap hidup saat layar mati, app di-background,
 * atau Doze mode aktif. Ini adalah komponen KRITIS untuk aplikasi darurat — tanpa FGS,
 * mesh mati dalam hitungan menit setelah layar mati.
 *
 * Lifecycle:
 *  - Dimulai dari MainActivity setelah izin diberikan dan identitas ada.
 *  - Tetap hidup selama baterai ada dan sistem tidak membunuhnya (START_STICKY untuk restart).
 *  - Dihentikan oleh: tombol "Stop Mesh" di notifikasi ATAU clearIdentity() dari user.
 *
 * Locks yang dipegang:
 *  - WifiLock (FULL_LOW_LATENCY / FULL_HIGH_PERF): cegah WiFi interface di-suspend
 *  - WakeLock (PARTIAL): CPU tetap aktif untuk memproses paket mesh
 */
class MeshForegroundService : Service() {

    companion object {
        private const val TAG = "MeshFGS"
        const val CHANNEL_ID = "caraka_mesh_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.caraka.ACTION_MESH_START"
        const val ACTION_STOP  = "com.example.caraka.ACTION_MESH_STOP"

        fun startIntent(context: Context): Intent =
            Intent(context, MeshForegroundService::class.java).apply { action = ACTION_START }

        fun stopIntent(context: Context): Intent =
            Intent(context, MeshForegroundService::class.java).apply { action = ACTION_STOP }
    }

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // EU-2.1: periodic DTN queue-processor. A timer-based sweep (NOT a transport hook, per D9/D10)
    // that drives outbox retries + carry delivery + expiry. Lives only while the service runs.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queueJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "ACTION_STOP diterima — menghentikan mesh dan service")
            stopMesh()
            stopSelf()
            return START_NOT_STICKY
        }

        // Tampilkan notifikasi persistent sebelum melakukan pekerjaan (Android 5+ requirement)
        startForeground(NOTIFICATION_ID, buildNotification("Mesh aktif — mencari peers..."))
        acquireLocks()
        startMesh()
        return START_STICKY  // Restart service jika dibunuh sistem
    }

    private fun startMesh() {
        try {
            (application as CarakaApp).transport.startListening()
            Log.d(TAG, "Mesh transport dimulai dari ForegroundService")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal start mesh transport", e)
        }
        startQueueProcessor()
    }

    private fun stopMesh() {
        queueJob?.cancel()
        queueJob = null
        try {
            (application as CarakaApp).transport.stopListening()
            Log.d(TAG, "Mesh transport dihentikan")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal stop mesh transport", e)
        }
        releaseLocks()
    }

    /**
     * EU-2.1: timer-based DTN queue-processor. Periodically sweeps the outbox — resending un-ACKed
     * unicast with bounded backoff, carrying messages for verified contacts, and evicting expired/
     * over-quota units (the actual policy lives in [MeshRepository.retryDueMessages], EU-1.4/2.2/2.3).
     * This is the periodic driver mandated by D10; it is idempotent and safe to run alongside the
     * lightweight on-send trigger from Phase 1.
     */
    private fun startQueueProcessor() {
        if (queueJob?.isActive == true) return
        val repository = (application as CarakaApp).repository
        queueJob = serviceScope.launch {
            while (isActive) {
                delay(MeshPolicy.QUEUE_PROCESSOR_TICK_MS)
                try {
                    repository.retryDueMessages()
                } catch (e: Exception) {
                    Log.e(TAG, "Queue-processor tick gagal", e)
                }
            }
        }
        Log.d(TAG, "DTN queue-processor dimulai (tiap ${MeshPolicy.QUEUE_PROCESSOR_TICK_MS}ms)")
    }

    private fun acquireLocks() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        else
            @Suppress("DEPRECATION")
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifiLock = wifiManager.createWifiLock(lockMode, "CarakaMeshWifiLock").apply {
            setReferenceCounted(false)
            acquire()
        }

        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CarakaMesh::WakeLock"
        ).apply {
            setReferenceCounted(false)
            // Acquire with timeout 12 jam — cukup untuk skenario darurat satu hari
            acquire(12 * 60 * 60 * 1000L)
        }
        Log.d(TAG, "WifiLock + WakeLock acquired")
    }

    private fun releaseLocks() {
        wifiLock?.takeIf { it.isHeld }?.release()
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
        wakeLock = null
        Log.d(TAG, "WifiLock + WakeLock released")
    }

    override fun onDestroy() {
        stopMesh()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CARAKA Mesh",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mesh komunikasi darurat CARAKA aktif di latar belakang"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CARAKA Mesh Aktif")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_caraka_logo_inset)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_delete, "Stop Mesh", stopIntent)
            .build()
    }
}
