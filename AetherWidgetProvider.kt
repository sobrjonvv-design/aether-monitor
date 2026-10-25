package com.aether.monitor.widget

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.widget.RemoteViews
import com.aether.monitor.MainActivity
import com.aether.monitor.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

/**
 * AETHER // Native Android Home Screen Widget Provider (4x2 / 4x4 / 2x2)
 * Features Real-Time OLED Noir Animation Engine (Solar Systems & Lightning)
 * With Speed Control (0.5X - 4.0X) and OLED Anti-Burn-In Pixel-Shifting
 */
class AetherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val prefs = context.getSharedPreferences("aether_prefs", Context.MODE_PRIVATE)
        val pinnedZenId = prefs.getInt("pinned_zen_id", 14)

        for (appWidgetId in appWidgetIds) {
            try {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Start live animation engine if user selected an animated game
        if (isAnimatedGame(pinnedZenId)) {
            startAnimationService(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        try {
            if (intent.action == ACTION_CYCLE_SPEED) {
                val prefs = context.getSharedPreferences("aether_prefs", Context.MODE_PRIVATE)
                val currentSpeed = prefs.getFloat("widget_speed", 1.0f)
                val nextSpeed = when {
                    currentSpeed < 0.8f -> 1.0f
                    currentSpeed < 1.5f -> 2.0f
                    currentSpeed < 3.0f -> 4.0f
                    else -> 0.5f
                }
                prefs.edit().putFloat("widget_speed", nextSpeed).apply()

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, AetherWidgetProvider::class.java)
                )
                for (id in ids) {
                    updateAppWidget(context, appWidgetManager, id)
                }
            } else {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, AetherWidgetProvider::class.java)
                )
                for (id in ids) {
                    updateAppWidget(context, appWidgetManager, id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val ACTION_CYCLE_SPEED = "com.aether.monitor.ACTION_CYCLE_SPEED"

        /**
         * Checks if the game has live continuous animation (Solar Systems & Lightning)
         * All other games display the static Nothing OS emblem as requested.
         */
        fun isAnimatedGame(gameId: Int): Boolean {
            return gameId == 14 || gameId == 13 || gameId == 16 || gameId == 11
        }

        fun startAnimationService(context: Context) {
            try {
                val serviceIntent = Intent(context, AetherAnimationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

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

            // 5. Active Pinned Zen Game Info
            val prefs = context.getSharedPreferences("aether_prefs", Context.MODE_PRIVATE)
            val pinnedZenId = prefs.getInt("pinned_zen_id", 14)
            val pinnedZenName = prefs.getString("pinned_zen_name", "Solar System Gravity")
            val speed = prefs.getFloat("widget_speed", 1.0f)

            views.setTextViewText(
                R.id.widget_footer,
                "▶ ZEN #${String.format("%02d", pinnedZenId)}: ${pinnedZenName?.uppercase()} [ИГРАТЬ]"
            )

            // Speed Control Button (Visible on animated games)
            if (isAnimatedGame(pinnedZenId)) {
                views.setViewVisibility(R.id.widget_speed_btn, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_speed_btn, "⚡ ${String.format(Locale.US, "%.1fX", speed)}")

                val speedIntent = Intent(context, AetherWidgetProvider::class.java).apply {
                    action = ACTION_CYCLE_SPEED
                }
                val piSpeed = PendingIntent.getBroadcast(
                    context,
                    888,
                    speedIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_speed_btn, piSpeed)
            } else {
                views.setViewVisibility(R.id.widget_speed_btn, android.view.View.GONE)
            }

            // 6. Real-Time Procedural Game Graphic Rendering (OLED Anti-Burn-In)
            try {
                val bw = 600
                val bh = 380
                val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.BLACK)

                val t = System.currentTimeMillis() / 1000.0
                if (isAnimatedGame(pinnedZenId)) {
                    renderAnimatedGame(canvas, bw, bh, pinnedZenId, t, speed)
                } else {
                    drawStaticEmblem(canvas, bw, bh, pinnedZenId, pinnedZenName ?: "Zen Mode")
                }

                views.setImageViewBitmap(R.id.widget_game_canvas, bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 7. Click Intent -> Launch Fullscreen Game on Tap
            try {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("zen_game_id", pinnedZenId)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    pinnedZenId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                views.setOnClickPendingIntent(R.id.widget_game_canvas, pendingIntent)
                views.setOnClickPendingIntent(R.id.widget_footer, pendingIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Update widget view
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun renderAnimatedGame(canvas: Canvas, w: Int, h: Int, gameId: Int, t: Double, speed: Float) {
            when (gameId) {
                14 -> drawSolarSystem(canvas, w, h, t, speed)
                13 -> drawEventHorizon(canvas, w, h, t, speed)
                16, 11 -> drawTeslaLightning(canvas, w, h, t, speed)
                else -> drawSolarSystem(canvas, w, h, t, speed)
            }
        }

        // #14: SOLAR SYSTEM GRAVITY (OLED Anti-Burn-In & Keplerian Orbit Animation)
        private fun drawSolarSystem(canvas: Canvas, w: Int, h: Int, t: Double, speed: Float) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Anti-burn-in sub-pixel Lissajous orbit shift
            val shiftX = (sin(t * 0.04) * 3.5f).toFloat()
            val shiftY = (cos(t * 0.03) * 3.5f).toFloat()
            val scx = cx + shiftX
            val scy = cy + shiftY

            // Central Sun with gentle corona pulse
            val sunPulse = (sin(t * speed * 2.0) * 1.5f).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.alpha = 230
            canvas.drawCircle(scx, scy, 13f + sunPulse, paint)

            paint.style = Paint.Style.STROKE
            paint.alpha = 60
            paint.strokeWidth = 1.5f
            canvas.drawCircle(scx, scy, 18f + sunPulse, paint)

            // 8 Planets: (orbital radius, planet radius, speed factor)
            val planets = listOf(
                Triple(35f, 2.2f, 4.1),   // Mercury
                Triple(52f, 3.2f, 2.6),   // Venus
                Triple(74f, 3.6f, 1.9),   // Earth (with Moon)
                Triple(95f, 2.8f, 1.4),   // Mars
                Triple(135f, 7.5f, 0.7),  // Jupiter
                Triple(175f, 6.2f, 0.5),  // Saturn (with Rings)
                Triple(215f, 4.5f, 0.35), // Uranus
                Triple(250f, 4.2f, 0.25)  // Neptune
            )

            paint.style = Paint.Style.STROKE
            for ((r, size, spdFactor) in planets) {
                if (r > w / 2f) continue
                // Orbit line (dim anti-burn-in)
                paint.color = Color.WHITE
                paint.alpha = 35
                paint.strokeWidth = 1f
                canvas.drawCircle(scx, scy, r, paint)

                // Keplerian angular speed adjusted by user speed multiplier
                val angle = (t * speed * spdFactor * 0.6 + r * 1.7) % (2 * PI)
                val px = scx + (r * cos(angle)).toFloat()
                val py = scy + (r * sin(angle)).toFloat()

                paint.style = Paint.Style.FILL
                paint.alpha = 230
                canvas.drawCircle(px, py, size, paint)

                // Earth's orbiting Moon
                if (r == 74f) {
                    val moonAngle = (t * speed * 7.0) % (2 * PI)
                    val mx = px + (7f * cos(moonAngle)).toFloat()
                    val my = py + (7f * sin(moonAngle)).toFloat()
                    paint.alpha = 180
                    canvas.drawCircle(mx, my, 1.2f, paint)
                }

                // Saturn's tilted rings
                if (r == 175f) {
                    paint.style = Paint.Style.STROKE
                    paint.alpha = 160
                    paint.strokeWidth = 1.5f
                    canvas.drawOval(RectF(px - 11f, py - 4.5f, px + 11f, py + 4.5f), paint)
                }
            }

            // Asteroid belt (dots between Mars and Jupiter: r = 110f..120f)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            for (i in 0 until 45) {
                val rAst = 110f + ((i * 13) % 15)
                val aAst = (i * 0.14 + t * speed * 0.15) % (2 * PI)
                val ax = scx + (rAst * cos(aAst)).toFloat()
                val ay = scy + (rAst * sin(aAst)).toFloat()
                paint.alpha = (50 + (i % 80)).coerceIn(40, 150)
                canvas.drawCircle(ax, ay, 1.2f, paint)
            }
        }

        // #13: EVENT HORIZON VORTEX (Cosmic Singularity & Relativistic Swirl)
        private fun drawEventHorizon(canvas: Canvas, w: Int, h: Int, t: Double, speed: Float) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            val shiftX = (sin(t * 0.05) * 3f).toFloat()
            val shiftY = (cos(t * 0.04) * 3f).toFloat()
            val scx = cx + shiftX
            val scy = cy + shiftY

            // Accretion disk rings
            paint.style = Paint.Style.STROKE
            for (i in 1..7) {
                val rx = 52f + i * 26f
                val ry = rx * 0.36f
                paint.color = Color.WHITE
                paint.alpha = (30 + i * 18).coerceIn(20, 200)
                paint.strokeWidth = 1.2f
                canvas.save()
                canvas.rotate(-18f, scx, scy)
                canvas.drawOval(RectF(scx - rx, scy - ry, scx + rx, scy + ry), paint)
                canvas.restore()
            }

            // 160 Swirling relativistic stars
            paint.style = Paint.Style.FILL
            val starCount = 160
            for (i in 0 until starCount) {
                val baseR = 46f + ((i * 19) % 185)
                val angularSpeed = (3.2 / sqrt(baseR.toDouble())) * speed
                val angle = (i * (2 * PI / starCount) * 5 + t * angularSpeed) % (2 * PI)
                val sx = scx + (baseR * cos(angle)).toFloat()
                val sy = scy + (baseR * 0.38f * sin(angle)).toFloat() - (sin(angle) * 10f).toFloat()

                // Relativistic Doppler beaming
                val doppler = ((sin(angle) + 1.2) / 2.2).coerceIn(0.2, 1.0)
                val brightness = (doppler * 220).toInt().coerceIn(40, 230)
                paint.color = Color.WHITE
                paint.alpha = brightness
                val starSize = if (i % 7 == 0) 2.4f else 1.4f
                canvas.drawCircle(sx, sy, starSize, paint)
            }

            // Black Hole Singularity Core
            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            canvas.drawCircle(scx, scy, 38f, paint)

            // Glowing Event Horizon Rim (Anti-burn-in pulse)
            val rimPulse = (sin(t * speed * 2.5) * 1.5f).toFloat()
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            paint.alpha = 230
            paint.strokeWidth = 2.2f
            canvas.drawCircle(scx, scy, 38f + rimPulse, paint)

            paint.alpha = 70
            paint.strokeWidth = 5.0f
            canvas.drawCircle(scx, scy, 41f + rimPulse, paint)
        }

        // #16 / #11: TESLA & ATMOSPHERIC LIGHTNING ARC
        private fun drawTeslaLightning(canvas: Canvas, w: Int, h: Int, t: Double, speed: Float) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // OLED Anti-burn-in subpixel shifting
            val shiftX = (sin(t * 0.05) * 3f).toFloat()
            val shiftY = (cos(t * 0.04) * 3f).toFloat()

            // 1. Two high-voltage electrodes (Top and Bottom)
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            paint.strokeWidth = 2.5f
            paint.alpha = 200
            canvas.drawArc(RectF(cx - 35f + shiftX, 15f + shiftY, cx + 35f + shiftX, 45f + shiftY), 0f, 180f, false, paint)
            canvas.drawArc(RectF(cx - 35f + shiftX, h - 45f + shiftY, cx + 35f + shiftX, h - 15f + shiftY), 180f, 180f, false, paint)

            // 2. High-frequency plasma flicker cycle
            val cycle = (t * speed * 8.0) % 10.0
            val intensity = if (cycle < 7.0) (sin(cycle * PI) * 190 + 55).toInt().coerceIn(60, 245) else 50

            // 3. Main lightning discharge branch (Jagged fractal path)
            val path = Path()
            val startX = cx + shiftX
            val startY = 45f + shiftY
            val endX = cx + ((sin(t * speed * 3.5) * 45f).toFloat()) + shiftX
            val endY = h - 45f + shiftY

            path.moveTo(startX, startY)
            val segments = 8
            val segH = (endY - startY) / segments
            var curX = startX
            var curY = startY

            for (s in 1 until segments) {
                val targetY = startY + s * segH
                val jitter = (sin(s * 2.3 + t * speed * 12.0) * 32f).toFloat()
                val targetX = curX + (endX - curX) / (segments - s + 1) + jitter
                path.lineTo(targetX, targetY)

                // Branching fork off the main discharge
                if (s == 3 || s == 5) {
                    val forkPath = Path()
                    forkPath.moveTo(targetX, targetY)
                    val forkDir = if (s == 3) -1f else 1f
                    val forkX1 = targetX + forkDir * (25f + (sin(t * 5.0) * 15f).toFloat())
                    val forkY1 = targetY + 20f
                    val forkX2 = forkX1 + forkDir * 20f
                    val forkY2 = forkY1 + 15f
                    forkPath.lineTo(forkX1, forkY1)
                    forkPath.lineTo(forkX2, forkY2)

                    paint.strokeWidth = 1.2f
                    paint.alpha = (intensity * 0.65f).toInt()
                    canvas.drawPath(forkPath, paint)
                }

                curX = targetX
                curY = targetY
            }
            path.lineTo(endX, endY)

            // Outer plasma glow
            paint.strokeWidth = 5.5f
            paint.alpha = (intensity * 0.35f).toInt()
            canvas.drawPath(path, paint)

            // Inner bright core
            paint.strokeWidth = 2.0f
            paint.alpha = intensity
            canvas.drawPath(path, paint)

            // Center spark nodes
            paint.style = Paint.Style.FILL
            paint.alpha = intensity
            canvas.drawCircle(startX, startY, 4f, paint)
            canvas.drawCircle(endX, endY, 4f, paint)
        }

        // STATIC NOTHING OS EMBLEM FOR ALL OTHER GAMES
        private fun drawStaticEmblem(canvas: Canvas, w: Int, h: Int, gameId: Int, name: String) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE

            paint.strokeWidth = 2f
            paint.alpha = 180
            canvas.drawCircle(cx, cy - 18f, 42f, paint)

            paint.strokeWidth = 1.2f
            paint.alpha = 100
            canvas.drawCircle(cx, cy - 18f, 26f, paint)

            paint.style = Paint.Style.FILL
            paint.alpha = 220
            canvas.drawCircle(cx, cy - 18f, 10f, paint)

            paint.textSize = 12f
            paint.textAlign = Paint.Align.CENTER
            paint.alpha = 140
            paint.color = Color.WHITE
            canvas.drawText("STATIC // EMBLEM MODE", cx, cy + 46f, paint)
        }
    }
}
