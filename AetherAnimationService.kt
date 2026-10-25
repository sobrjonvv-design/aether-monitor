package com.aether.monitor.widget

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.aether.monitor.R

/**
 * AETHER // Live OLED Home Screen Animation Engine
 * Automatically drives real-time 60-frame procedural animation loop
 * Optimized with Screen-State detection (0% battery drain when screen is off)
 */
class AetherAnimationService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isScreenOn = true
    private var isRunning = false
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    isScreenOn = true
                    scheduleNextTick(0)
                }
            }
        }
    }

    private val tickerRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            if (isScreenOn) {
                try {
                    val mgr = AppWidgetManager.getInstance(this@AetherAnimationService)
                    val component = ComponentName(this@AetherAnimationService, AetherWidgetProvider::class.java)
                    val ids = mgr.getAppWidgetIds(component)
                    if (ids.isEmpty()) {
                        stopSelf()
                        return
                    }

                    val prefs = getSharedPreferences("aether_prefs", Context.MODE_PRIVATE)
                    val pinnedZenId = prefs.getInt("pinned_zen_id", 14)

                    // Only animate the user-chosen games (Solar System 14, Event Horizon 13, Lightning 16/11)
                    if (AetherWidgetProvider.isAnimatedGame(pinnedZenId)) {
                        for (id in ids) {
                            AetherWidgetProvider.updateAppWidget(this@AetherAnimationService, mgr, id)
                        }
                    } else {
                        // Unanimated game (emblem mode), stop ticker
                        stopSelf()
                        return
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val prefs = getSharedPreferences("aether_prefs", Context.MODE_PRIVATE)
            val speed = prefs.getFloat("widget_speed", 1.0f)
            val delay = when {
                speed >= 3.5f -> 80L
                speed >= 1.8f -> 120L
                speed <= 0.6f -> 260L
                else -> 160L
            }
            scheduleNextTick(delay)
        }
    }

    private fun scheduleNextTick(delay: Long) {
        handler.removeCallbacks(tickerRunnable)
        if (isRunning && isScreenOn) {
            handler.postDelayed(tickerRunnable, delay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        setupForeground()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            registerReceiver(screenReceiver, filter)
            receiverRegistered = true
        } catch (e: Exception) {}

        scheduleNextTick(0)
    }

    @SuppressLint("ForegroundServiceType")
    private fun setupForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channelId = "aether_live_engine"
                val nm = getSystemService(NotificationManager::class.java)
                if (nm != null && nm.getNotificationChannel(channelId) == null) {
                    val chan = NotificationChannel(
                        channelId,
                        "AETHER Live Engine",
                        NotificationManager.IMPORTANCE_MIN
                    ).apply {
                        description = "OLED Home Screen Widget Animation Engine"
                        setShowBadge(false)
                        enableLights(false)
                        enableVibration(false)
                    }
                    nm.createNotificationChannel(chan)
                }

                val notification = Notification.Builder(this, channelId)
                    .setContentTitle("AETHER // OLED Live Engine")
                    .setContentText("Active animation // Anti-burn-in safe")
                    .setSmallIcon(R.drawable.ic_concentric_logo)
                    .setOngoing(true)
                    .build()

                startForeground(1001, notification)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            scheduleNextTick(0)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(tickerRunnable)
        if (receiverRegistered) {
            try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
            receiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
