package com.aether.monitor.widget

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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
import android.widget.RemoteViews
import com.aether.monitor.MainActivity
import com.aether.monitor.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

/**
 * AETHER // Native Android Home Screen Widget Provider (4x2 / 4x4 / 2x2)
 * Features Real-Time OLED Noir Game Graphics Engine & System Telemetry
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

            // 5. Active Pinned Zen Game Info
            val prefs = context.getSharedPreferences("aether_prefs", Context.MODE_PRIVATE)
            val pinnedZenId = prefs.getInt("pinned_zen_id", 13)
            val pinnedZenName = prefs.getString("pinned_zen_name", "Event Horizon Vortex")

            views.setTextViewText(
                R.id.widget_footer,
                "▶ ZEN #${String.format("%02d", pinnedZenId)}: ${pinnedZenName?.uppercase()} [ИГРАТЬ]"
            )

            // 6. Real-Time Procedural Game Graphic Rendering
            try {
                val bw = 600
                val bh = 380
                val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.BLACK)

                val t = System.currentTimeMillis() / 1000.0
                renderGameVisual(canvas, bw, bh, pinnedZenId, t)

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
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Update widget view
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * Procedural OLED Monochrome Renderers for all 16 Zen games
         */
        private fun renderGameVisual(canvas: Canvas, w: Int, h: Int, gameId: Int, t: Double) {
            when (gameId) {
                13 -> drawEventHorizon(canvas, w, h, t)
                14 -> drawSolarSystem(canvas, w, h, t)
                1 -> drawKineticSand(canvas, w, h, t)
                2 -> drawBonsai(canvas, w, h, t)
                3 -> drawLiquidMercury(canvas, w, h, t)
                4 -> drawClicker(canvas, w, h, t)
                5 -> drawOrbitalSlingshot(canvas, w, h, t)
                6 -> drawMagneticFilings(canvas, w, h, t)
                7 -> drawKaleido(canvas, w, h, t)
                8 -> drawConstellations(canvas, w, h, t)
                9 -> drawMorseTelegraph(canvas, w, h, t)
                10 -> drawPhotonCloud(canvas, w, h, t)
                11 -> drawMycelium(canvas, w, h, t)
                12 -> drawNeuralWeb(canvas, w, h, t)
                15 -> drawSpacetimeFabric(canvas, w, h, t)
                16 -> drawNixieTube(canvas, w, h, t)
                else -> drawEventHorizon(canvas, w, h, t)
            }
        }

        // #13: EVENT HORIZON VORTEX (Black Hole & Relativistic Accretion Disk)
        private fun drawEventHorizon(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Accretion disk rings
            paint.style = Paint.Style.STROKE
            for (i in 1..7) {
                val rx = 55f + i * 26f
                val ry = rx * 0.36f
                paint.color = Color.WHITE
                paint.alpha = (35 + i * 20).coerceIn(20, 240)
                paint.strokeWidth = 1.2f
                canvas.save()
                canvas.rotate(-18f, cx, cy)
                canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
                canvas.restore()
            }

            // 150 Swirling relativistic stars
            paint.style = Paint.Style.FILL
            val starCount = 150
            for (i in 0 until starCount) {
                val baseR = 48f + ((i * 19) % 180)
                val speed = 2.6 / sqrt(baseR.toDouble())
                val angle = (i * (2 * PI / starCount) * 5 + t * speed) % (2 * PI)
                val sx = cx + (baseR * cos(angle)).toFloat()
                val sy = cy + (baseR * 0.38f * sin(angle)).toFloat() - (sin(angle) * 10f).toFloat()

                val brightness = ((sin(angle + t * 1.5) + 1.2) / 2.2 * 255).toInt().coerceIn(50, 255)
                paint.color = Color.WHITE
                paint.alpha = brightness
                val starSize = if (i % 6 == 0) 2.5f else 1.5f
                canvas.drawCircle(sx, sy, starSize, paint)
            }

            // Black Hole Singularity Core
            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            canvas.drawCircle(cx, cy, 38f, paint)

            // Glowing Event Horizon Rim
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            paint.alpha = 245
            paint.strokeWidth = 2.4f
            canvas.drawCircle(cx, cy, 38f, paint)

            paint.alpha = 90
            paint.strokeWidth = 6.0f
            canvas.drawCircle(cx, cy, 41f, paint)
        }

        // #14: SOLAR SYSTEM GRAVITY
        private fun drawSolarSystem(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Sun in center
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.alpha = 255
            canvas.drawCircle(cx, cy, 14f, paint)

            paint.style = Paint.Style.STROKE
            paint.alpha = 90
            paint.strokeWidth = 2f
            canvas.drawCircle(cx, cy, 19f, paint)

            val planets = listOf(
                Pair(35f, 2.2f), Pair(52f, 3.2f), Pair(74f, 3.6f),
                Pair(95f, 2.8f), Pair(135f, 7.5f), Pair(175f, 6.2f),
                Pair(215f, 4.5f), Pair(250f, 4.2f)
            )

            paint.style = Paint.Style.STROKE
            for ((r, size) in planets) {
                if (r > w / 2f) continue
                paint.color = Color.WHITE
                paint.alpha = 40
                paint.strokeWidth = 1f
                canvas.drawCircle(cx, cy, r, paint)

                val speed = 1.8 / (sqrt(r.toDouble()) * 0.15)
                val angle = (t * speed * 0.25 + r) % (2 * PI)
                val px = cx + (r * cos(angle)).toFloat()
                val py = cy + (r * sin(angle)).toFloat()

                paint.style = Paint.Style.FILL
                paint.alpha = 255
                canvas.drawCircle(px, py, size, paint)

                if (r == 175f) {
                    paint.style = Paint.Style.STROKE
                    paint.alpha = 180
                    paint.strokeWidth = 1.5f
                    canvas.drawOval(RectF(px - 11f, py - 4f, px + 11f, py + 4f), paint)
                }
            }

            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            for (i in 0 until 50) {
                val rAst = 110f + ((i * 13) % 15)
                val aAst = (i * 0.13 + t * 0.08) % (2 * PI)
                val ax = cx + (rAst * cos(aAst)).toFloat()
                val ay = cy + (rAst * sin(aAst)).toFloat()
                paint.alpha = (80 + (i % 100)).coerceIn(50, 180)
                canvas.drawCircle(ax, ay, 1.2f, paint)
            }
        }

        // #15: SPACETIME GRAVITY FABRIC
        private fun drawSpacetimeFabric(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val cy = h / 2f + 20f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE

            val cols = 14
            val rows = 9
            val dx = w / (cols + 1f)
            val dy = (h - 40f) / (rows + 1f)

            fun warp(x: Float, y: Float): Pair<Float, Float> {
                val dist = sqrt((x - cx).pow(2) + (y - cy).pow(2))
                val depth = 55f / (1f + (dist / 65f).pow(2))
                return Pair(x, y + depth)
            }

            for (r in 1..rows) {
                val path = Path()
                val y = r * dy + 20f
                paint.alpha = (50 + r * 15).coerceIn(40, 200)
                paint.strokeWidth = 1f
                for (c in 1..cols) {
                    val x = c * dx
                    val (wx, wy) = warp(x, y)
                    if (c == 1) path.moveTo(wx, wy) else path.lineTo(wx, wy)
                }
                canvas.drawPath(path, paint)
            }

            for (c in 1..cols) {
                val path = Path()
                val x = c * dx
                paint.alpha = (60 + ((c % 4) * 20)).coerceIn(40, 180)
                for (r in 1..rows) {
                    val y = r * dy + 20f
                    val (wx, wy) = warp(x, y)
                    if (r == 1) path.moveTo(wx, wy) else path.lineTo(wx, wy)
                }
                canvas.drawPath(path, paint)
            }

            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.alpha = 255
            canvas.drawCircle(cx, cy + 45f, 9f, paint)
        }

        // #12: CYBER-SYNAPSE NEURAL WEB
        private fun drawNeuralWeb(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            val nodes = listOf(
                Pair(cx - 160f, cy - 70f), Pair(cx - 150f, cy + 60f),
                Pair(cx - 80f, cy - 110f), Pair(cx - 70f, cy), Pair(cx - 85f, cy + 90f),
                Pair(cx, cy - 60f), Pair(cx, cy + 40f),
                Pair(cx + 80f, cy - 90f), Pair(cx + 70f, cy + 20f), Pair(cx + 85f, cy + 100f),
                Pair(cx + 160f, cy - 50f), Pair(cx + 150f, cy + 70f)
            )

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f
            for (i in nodes.indices) {
                for (j in (i + 1) until nodes.size) {
                    val d = sqrt((nodes[i].first - nodes[j].first).pow(2) + (nodes[i].second - nodes[j].second).pow(2))
                    if (d < 130f) {
                        val pulse = (sin(t * 3.0 + i + j) + 1.0) / 2.0
                        paint.color = Color.WHITE
                        paint.alpha = (30 + pulse * 180).toInt().coerceIn(30, 240)
                        canvas.drawLine(nodes[i].first, nodes[i].second, nodes[j].first, nodes[j].second, paint)
                    }
                }
            }

            paint.style = Paint.Style.FILL
            for ((idx, node) in nodes.withIndex()) {
                val glow = (sin(t * 2.5 + idx) + 1.0) / 2.0
                paint.color = Color.WHITE
                paint.alpha = 255
                canvas.drawCircle(node.first, node.second, 3.5f + glow.toFloat() * 2f, paint)

                paint.style = Paint.Style.STROKE
                paint.alpha = (glow * 180).toInt()
                paint.strokeWidth = 1f
                canvas.drawCircle(node.first, node.second, 8f + glow.toFloat() * 4f, paint)
                paint.style = Paint.Style.FILL
            }
        }

        // #1: KINETIC SAND
        private fun drawKineticSand(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE

            val count = 160
            for (i in 0 until count) {
                val angle = i * 0.22 + t * 0.4
                val r = 25f + ((i * 19) % 160)
                val px = cx + (r * cos(angle) * (1.0 + 0.2 * sin(angle * 3))).toFloat()
                val py = cy + (r * sin(angle) * 0.65).toFloat()
                paint.alpha = ((i * 37) % 200 + 55).coerceIn(40, 255)
                canvas.drawCircle(px, py, 1.8f, paint)
            }

            paint.style = Paint.Style.STROKE
            paint.alpha = 120
            paint.strokeWidth = 1.5f
            canvas.drawCircle(cx - 60f, cy, 14f, paint)
            canvas.drawCircle(cx + 60f, cy, 14f, paint)
        }

        // #2: BONSAI FRACTAL TRIMMER
        private fun drawBonsai(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val baseCy = h - 35f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            paint.strokeCap = Paint.Cap.ROUND

            paint.strokeWidth = 2f
            paint.alpha = 220
            canvas.drawRect(cx - 65f, baseCy, cx + 65f, baseCy + 14f, paint)

            fun branch(x: Float, y: Float, len: Float, angle: Float, depth: Int) {
                if (depth <= 0) {
                    paint.style = Paint.Style.FILL
                    paint.alpha = 200
                    canvas.drawCircle(x, y, 4.5f, paint)
                    paint.style = Paint.Style.STROKE
                    return
                }
                val rad = Math.toRadians(angle.toDouble())
                val x2 = (x + len * sin(rad)).toFloat()
                val y2 = (y - len * cos(rad)).toFloat()
                paint.strokeWidth = depth.toFloat() * 1.5f
                paint.alpha = (100 + depth * 30).coerceIn(80, 255)
                canvas.drawLine(x, y, x2, y2, paint)

                val sway = (sin(t * 1.2 + depth) * 3f).toFloat()
                branch(x2, y2, len * 0.74f, angle - 26f + sway, depth - 1)
                branch(x2, y2, len * 0.70f, angle + 24f + sway, depth - 1)
            }

            branch(cx, baseCy, 50f, -4f, 5)
        }

        // #3: LIQUID MERCURY POOL
        private fun drawLiquidMercury(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE

            for (i in 1..8) {
                val r = (i * 26f + (t * 20.0).toFloat()) % 220f
                val alpha = (255 * (1f - (r / 220f))).toInt().coerceIn(0, 255)
                paint.alpha = alpha
                paint.strokeWidth = 2.0f
                canvas.drawCircle(cx, cy, r, paint)
            }
        }

        // #4: MECHANICAL TALLY CLICKER
        private fun drawClicker(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            paint.strokeWidth = 3f

            canvas.drawRoundRect(RectF(cx - 100f, cy - 60f, cx + 100f, cy + 60f), 20f, 20f, paint)
            canvas.drawRect(RectF(cx - 70f, cy - 35f, cx + 70f, cy + 35f), paint)

            paint.style = Paint.Style.FILL
            paint.textSize = 42f
            paint.textAlign = Paint.Align.CENTER
            val count = ((t * 2) % 9999).toInt()
            canvas.drawText(String.format(Locale.US, "%04d", count), cx, cy + 15f, paint)
        }

        // #5: ORBITAL SLINGSHOT
        private fun drawOrbitalSlingshot(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, 16f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            for (i in 1..3) {
                val a = 70f * i
                val b = 40f * i
                paint.alpha = 80
                canvas.drawOval(RectF(cx - a, cy - b, cx + a, cy + b), paint)

                val angle = t * (2.0 / i)
                val sx = cx + (a * cos(angle)).toFloat()
                val sy = cy + (b * sin(angle)).toFloat()
                paint.style = Paint.Style.FILL
                paint.alpha = 255
                canvas.drawCircle(sx, sy, 4f, paint)
                paint.style = Paint.Style.STROKE
            }
        }

        // #6: MAGNETIC IRON FILINGS
        private fun drawMagneticFilings(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.WHITE
            paint.strokeWidth = 1.5f

            for (gx in -4..4) {
                for (gy in -3..3) {
                    val px = cx + gx * 45f
                    val py = cy + gy * 35f
                    val angle = atan2((py - cy).toDouble(), (px - cx).toDouble()) + PI / 2
                    val len = 12f
                    val x1 = (px - len * cos(angle)).toFloat()
                    val y1 = (py - len * sin(angle)).toFloat()
                    val x2 = (px + len * cos(angle)).toFloat()
                    val y2 = (py + len * sin(angle)).toFloat()
                    paint.alpha = 180
                    canvas.drawLine(x1, y1, x2, y2, paint)
                }
            }
        }

        // #7: KALEIDO PAINTER
        private fun drawKaleido(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            paint.strokeWidth = 1.8f

            canvas.save()
            canvas.rotate((t * 15).toFloat(), cx, cy)
            for (i in 0 until 8) {
                canvas.rotate(45f, cx, cy)
                paint.alpha = 180
                canvas.drawOval(RectF(cx - 30f, cy - 80f, cx + 30f, cy + 80f), paint)
                canvas.drawCircle(cx, cy - 70f, 12f, paint)
            }
            canvas.restore()
        }

        // #8: CONSTELLATIONS HARP
        private fun drawConstellations(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            val stars = listOf(
                Pair(cx - 120f, cy - 60f), Pair(cx - 60f, cy - 90f),
                Pair(cx, cy - 40f), Pair(cx + 70f, cy - 80f),
                Pair(cx + 130f, cy - 30f), Pair(cx + 80f, cy + 50f),
                Pair(cx, cy + 70f), Pair(cx - 90f, cy + 40f)
            )

            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            paint.alpha = 140
            paint.strokeWidth = 1.2f
            for (i in 0 until stars.size - 1) {
                canvas.drawLine(stars[i].first, stars[i].second, stars[i + 1].first, stars[i + 1].second, paint)
            }

            paint.style = Paint.Style.FILL
            for (s in stars) {
                paint.alpha = 255
                canvas.drawCircle(s.first, s.second, 3.5f, paint)
            }
        }

        // #9: MORSE TELEGRAPH
        private fun drawMorseTelegraph(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            paint.strokeWidth = 4f

            // Waveform pulses
            val startX = cx - 180f
            var curX = startX
            val py = cy
            for (i in 0 until 12) {
                val isDash = (i % 3 == 0)
                val len = if (isDash) 30f else 10f
                paint.alpha = 220
                canvas.drawLine(curX, py - 15f, curX + len, py - 15f, paint)
                curX += len + 15f
            }
        }

        // #10: QUANTUM PHOTON CLOUD
        private fun drawPhotonCloud(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE

            for (i in 0 until 70) {
                val a = i * 0.4 + t * 0.5
                val r = (i * 7f) % 150f
                val px = cx + (r * cos(a)).toFloat()
                val py = cy + (r * sin(a)).toFloat()
                paint.alpha = ((i * 19) % 200 + 55).coerceIn(50, 255)
                canvas.drawCircle(px, py, 2.2f, paint)
            }
        }

        // #11: BIOLUMINESCENT MYCELIUM
        private fun drawMycelium(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE

            for (i in 0 until 10) {
                val angle = i * (2 * PI / 10)
                var px = cx
                var py = cy
                paint.strokeWidth = 1.5f
                paint.alpha = 170
                for (step in 1..6) {
                    val nx = px + (cos(angle) * 22f + sin(t + step) * 8f).toFloat()
                    val ny = py + (sin(angle) * 22f + cos(t + step) * 8f).toFloat()
                    canvas.drawLine(px, py, nx, ny, paint)
                    px = nx
                    py = ny
                }
            }
        }

        // #16: NIXIE TUBE TESLA COUNTER
        private fun drawNixieTube(canvas: Canvas, w: Int, h: Int, t: Double) {
            val cx = w / 2f
            val cy = h / 2f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.STROKE
            paint.color = Color.WHITE
            paint.strokeWidth = 2f

            val tubeW = 48f
            val tubeH = 80f
            val gap = 16f
            val startX = cx - 2 * (tubeW + gap) + gap / 2f

            for (i in 0 until 4) {
                val tx = startX + i * (tubeW + gap)
                paint.alpha = 140
                canvas.drawRoundRect(RectF(tx, cy - tubeH / 2, tx + tubeW, cy + tubeH / 2), 10f, 10f, paint)

                paint.style = Paint.Style.FILL
                paint.textSize = 34f
                paint.textAlign = Paint.Align.CENTER
                paint.alpha = 255
                val digit = ((t + i * 3) % 10).toInt()
                canvas.drawText("$digit", tx + tubeW / 2, cy + 12f, paint)
                paint.style = Paint.Style.STROKE
            }
        }
    }
}
