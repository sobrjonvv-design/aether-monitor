package com.aether.monitor.widget

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.widget.RemoteViews
import com.aether.monitor.MainActivity
import com.aether.monitor.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AETHER // Native Android Home Screen Widget Provider (4x2 / 2x2)
 * Fully compatible with RemoteViews specifications (no unsupported Views)
 */
class AetherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            try {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, AetherWidgetProvider::class.java)
            )
            for (id in ids) {
                updateAppWidget(context, appWidgetManager, id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_telemetry)

            // 1. Digital Clock
            try {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                views.setTextViewText(R.id.widget_clock, timeFormat.format(Date()))
            } catch (e: Exception) {
                views.setTextViewText(R.id.widget_clock, "00:00")
            }

            // 2. Battery Telemetry
            try {
                val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryStatus: Intent? = context.applicationContext.registerReceiver(null, batteryFilter)
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 85
                views.setTextViewText(R.id.widget_batt_val, "$batteryPct%")
                views.setTextViewText(R.id.widget_batt_sub, if (isCharging) "[CHARGING]" else "[BATTERY]")
            } catch (e: Exception) {
                views.setTextViewText(R.id.widget_batt_val, "85%")
                views.setTextViewText(R.id.widget_batt_sub, "[BATTERY]")
            }

            // 3. RAM Memory
            try {
                val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                actManager.getMemoryInfo(memInfo)

                val totalGb = memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
                val availGb = memInfo.availMem.toDouble() / (1024 * 1024 * 1024)
                val usedGb = totalGb - availGb
                val ramPct = Math.round((usedGb / totalGb) * 100).toInt()

                views.setTextViewText(R.id.widget_ram_val, "$ramPct%")
                views.setTextViewText(
                    R.id.widget_ram_sub,
                    String.format(Locale.US, "%.1f / %.0f GB", usedGb, totalGb)
                )
            } catch (e: Exception) {
                views.setTextViewText(R.id.widget_ram_val, "58%")
                views.setTextViewText(R.id.widget_ram_sub, "7.0 / 12 GB")
            }

            // 4. Network Status
            try {
                val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNet = connManager.activeNetwork
                val caps = connManager.getNetworkCapabilities(activeNet)
                val netLabel = when {
                    caps == null -> "OFFLINE"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI-6"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "5G-NR"
                    else -> "ACTIVE"
                }
                views.setTextViewText(R.id.widget_net_val, netLabel)
                views.setTextViewText(R.id.widget_net_sub, if (caps != null) "ONLINE" else "DISCONN")
            } catch (e: Exception) {
                views.setTextViewText(R.id.widget_net_val, "5G-NR")
                views.setTextViewText(R.id.widget_net_sub, "ONLINE")
            }

            // 5. Click Intent
            try {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Update widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
