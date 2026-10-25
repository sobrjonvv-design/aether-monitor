package com.aether.monitor

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.aether.monitor.widget.AetherWidgetProvider

/**
 * AETHER // Native Host Activity
 * Lightweight, crash-proof native Activity with hardware-accelerated OLED WebView
 * & Native Home Screen AppWidget Pinning Bridge
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Seamless black fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        setContentView(webView)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        loadFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            loadFromIntent(intent)
        }
    }

    private fun loadFromIntent(intent: Intent?) {
        val zenGameId = intent?.getIntExtra("zen_game_id", 0) ?: 0
        if (zenGameId > 0) {
            webView.loadUrl("file:///android_asset/www/index.html?zen=$zenGameId&fullscreen=1")
        } else {
            webView.loadUrl("file:///android_asset/www/index.html")
        }
    }

    inner class AndroidBridge(private val ctx: Context) {
        @JavascriptInterface
        fun pinActiveGame(gameId: Int, gameName: String) {
            runOnUiThread {
                try {
                    // 1. Save active game to SharedPreferences for the Native Widget
                    val prefs = ctx.getSharedPreferences("aether_prefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putInt("pinned_zen_id", gameId)
                        .putString("pinned_zen_name", gameName)
                        .apply()

                    // 2. Refresh any active AETHER widgets on the home screen
                    val appWidgetManager = AppWidgetManager.getInstance(ctx)
                    val component = ComponentName(ctx, AetherWidgetProvider::class.java)
                    val ids = appWidgetManager.getAppWidgetIds(component)
                    for (id in ids) {
                        AetherWidgetProvider.updateAppWidget(ctx, appWidgetManager, id)
                    }

                    // 3. Request native Android 8.0+ Home Screen Widget Pinning
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (appWidgetManager.isRequestPinAppWidgetSupported) {
                            appWidgetManager.requestPinAppWidget(component, null, null)
                            Toast.makeText(
                                ctx,
                                "Добавление виджета игры #$gameId на рабочий стол...",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                ctx,
                                "Виджет обновлен! Зажмите пустой экран и выберите Виджеты -> AETHER",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            ctx,
                            "Виджет AETHER обновлен с игрой #$gameId!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
