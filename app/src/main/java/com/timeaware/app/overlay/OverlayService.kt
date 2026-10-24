package com.timeaware.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.timeaware.app.R
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OverlayService : Service() {
    companion object {
        @Volatile var isRunning: Boolean = false
            private set
    }
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var overlay: TextView? = null
    private var currentPackage: String? = null
    private var currentStartElapsed = 0L
    private var todayTotals = mutableMapOf<String, Long>()
    private var lastEventWallTime = 0L
    private var dayStart = 0L
    private lateinit var wm: WindowManager

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(4102, notification())
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        createOverlay()
        seedUsageTotals()
        executor.scheduleAtFixedRate({ runCatching { pollUsageEvents() } },
            0, 1, TimeUnit.SECONDS)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                "timeaware_monitor", "TimeAware monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, "timeaware_monitor")
            .setContentTitle("TimeAware is active")
            .setContentText("Showing your daily app timer")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .build()

    private fun createOverlay() {
        val view = TextView(this).apply {
            text = "TimeAware · waiting for app"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(28, 14, 28, 14)
            background = GradientDrawable().apply {
                setColor(Color.rgb(29, 30, 42))
                cornerRadius = 48f
                setStroke(2, Color.rgb(115, 97, 180))
            }
            elevation = 12f
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 72
        }
        try {
            wm.addView(view, params)
            overlay = view
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun seedUsageTotals() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        dayStart = cal.timeInMillis
        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, System.currentTimeMillis())
            .forEach { stats ->
                todayTotals[stats.packageName] = stats.totalTimeInForeground
            }
        lastEventWallTime = System.currentTimeMillis() - 60_000L
    }

    private fun pollUsageEvents() {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis != dayStart) {
            todayTotals.clear()
            dayStart = cal.timeInMillis
            lastEventWallTime = dayStart
        }

        val manager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = manager.queryEvents(lastEventWallTime, now)
        val event = UsageEvents.Event()
        var newest = lastEventWallTime

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.timeStamp > newest) newest = event.timeStamp
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    switchTo(event.packageName, event.timeStamp)
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (currentPackage == event.packageName) switchTo(null, event.timeStamp)
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> switchTo(null, event.timeStamp)
            }
        }
        lastEventWallTime = maxOf(newest, now - 1000L)

        val pkg = currentPackage
        if (pkg == null || pkg == packageName || !Settings.canDrawOverlays(this)) {
            overlay?.visibility = android.view.View.GONE
            return
        }
        overlay?.visibility = android.view.View.VISIBLE
        val accumulated = (todayTotals[pkg] ?: 0L) +
            if (currentStartElapsed > 0L) (SystemClock.elapsedRealtime() - currentStartElapsed) else 0L
        val seconds = accumulated / 1000L
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { pkg.substringAfterLast('.') }
        overlay?.text = "$label · ${seconds / 3600L}:${((seconds % 3600L) / 60L).toString().padStart(2, '0')}:${(seconds % 60L).toString().padStart(2, '0')}"
    }

    private fun switchTo(pkg: String?, eventWallTime: Long) {
        val previous = currentPackage
        if (previous != null && currentStartElapsed > 0L) {
            val elapsed = (eventWallTime - wallTimeFromElapsed(currentStartElapsed)).coerceAtLeast(0L)
            todayTotals[previous] = (todayTotals[previous] ?: 0L) + elapsed
        }
        currentPackage = pkg
        currentStartElapsed = if (pkg != null) SystemClock.elapsedRealtime() else 0L
    }

    private fun wallTimeFromElapsed(elapsed: Long): Long =
        System.currentTimeMillis() - (SystemClock.elapsedRealtime() - elapsed)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        executor.shutdownNow()
        try { overlay?.let { wm.removeView(it) } } catch (_: Exception) {}
        overlay = null
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
