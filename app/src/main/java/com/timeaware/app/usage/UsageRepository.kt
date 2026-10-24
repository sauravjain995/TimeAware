package com.timeaware.app.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Calendar

object UsageRepository {
    data class AppUsage(
        val packageName: String,
        val label: String,
        val durationMs: Long
    )

    fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun getTodayUsage(context: Context): List<AppUsage> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        val launcherLabels = launcherApps(context)
        val start = startOfToday()
        val end = System.currentTimeMillis()
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        return stats.asSequence()
            .filter { it.totalTimeInForeground > 0L && it.packageName != context.packageName }
            .groupBy { it.packageName }
            .mapNotNull { (pkg, entries) ->
                val duration = entries.maxOfOrNull { it.totalTimeInForeground } ?: 0L
                if (duration <= 0L) return@mapNotNull null
                val label = launcherLabels[pkg] ?: try {
                    @Suppress("DEPRECATION")
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    pkg.substringAfterLast('.')
                }
                AppUsage(pkg, label, duration)
            }
            .sortedByDescending { it.durationMs }
    }

    fun getUsageForPackageToday(context: Context, packageName: String): Long {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        return manager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startOfToday(), System.currentTimeMillis()
        ).filter { it.packageName == packageName }.maxOfOrNull { it.totalTimeInForeground } ?: 0L
    }

    fun launcherApps(context: Context): Map<String, String> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(intent, 0).associate {
            it.activityInfo.packageName to it.loadLabel(pm).toString()
        }
    }
}
