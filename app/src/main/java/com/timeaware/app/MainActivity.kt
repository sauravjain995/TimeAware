package com.timeaware.app

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.timeaware.app.overlay.OverlayService
import com.timeaware.app.usage.UsageRepository
import kotlinx.coroutines.delay

private val Bg = Color(0xFF0C0D12)
private val Panel = Color(0xFF191B25)
private val PanelLight = Color(0xFF222431)
private val Lavender = Color(0xFFB9A5FF)
private val Mint = Color(0xFF7FE0B1)
private val Muted = Color(0xFF9A9CAC)

class MainActivity : ComponentActivity() {
    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Lavender, background = Bg, surface = Panel)) {
                TimeAwareDashboard(
                    hasUsageAccess = ::hasUsageAccess,
                    hasOverlayAccess = { Settings.canDrawOverlays(this) },
                    openUsageSettings = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    openOverlaySettings = { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) },
                    startTimer = {
                        if (hasUsageAccess() && Settings.canDrawOverlays(this)) {
                            ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
                        }
                    },
                    stopTimer = { stopService(Intent(this, OverlayService::class.java)) }
                )
            }
        }
    }
}

@Composable
private fun TimeAwareDashboard(
    hasUsageAccess: () -> Boolean,
    hasOverlayAccess: () -> Boolean,
    openUsageSettings: () -> Unit,
    openOverlaySettings: () -> Unit,
    startTimer: () -> Unit,
    stopTimer: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var usageGranted by remember { mutableStateOf(hasUsageAccess()) }
    var overlayGranted by remember { mutableStateOf(hasOverlayAccess()) }
    var apps by remember { mutableStateOf(emptyList<UsageRepository.AppUsage>()) }
    var timerEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            usageGranted = hasUsageAccess()
            overlayGranted = hasOverlayAccess()
            if (usageGranted) apps = runCatching { UsageRepository.getTodayUsage(context) }.getOrDefault(emptyList())
            timerEnabled = OverlayService.isRunning
            delay(2000)
        }
    }

    val total = apps.sumOf { it.durationMs }
    val top = apps.take(8)
    val maxUsage = (top.maxOfOrNull { it.durationMs } ?: 1L).coerceAtLeast(1L)

    Surface(Modifier.fillMaxSize(), color = Bg) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("TimeAware", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = (-0.8).sp)
                        Text("Your time, made visible.", color = Muted, fontSize = 15.sp)
                    }
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(PanelLight), contentAlignment = Alignment.Center) {
                        Text("◷", color = Lavender, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = PanelLight)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("TODAY'S SCREEN TIME", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Mint))
                            Spacer(Modifier.width(6.dp))
                            Text("TODAY", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(if (usageGranted) formatDuration(total) else "—", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp)
                        Text("Across apps with available usage data", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatChip("TRACKED APPS", if (usageGranted) apps.size.toString() else "—", Modifier.weight(1f))
                            StatChip("TOP APP", if (usageGranted && apps.isNotEmpty()) apps.first().label.take(12) else "Not yet", Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Floating timer", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("A small pill that stays above other apps", color = Muted, fontSize = 13.sp)
                    }
                    Box(Modifier.clip(CircleShape).background(if (timerEnabled) Color(0xFF173C31) else Panel).padding(horizontal = 11.dp, vertical = 7.dp)) {
                        Text(if (timerEnabled) "ACTIVE" else "OFF", color = if (timerEnabled) Mint else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PermissionItem("Usage access", "Reads today's app usage totals", usageGranted, "Grant") { openUsageSettings() }
                        HorizontalDivider(color = Color(0xFF2B2D39))
                        PermissionItem("Display over other apps", "Allows the floating timer pill", overlayGranted, "Grant") { openOverlaySettings() }
                        Button(
                            onClick = { if (timerEnabled) stopTimer() else startTimer() },
                            enabled = usageGranted && overlayGranted,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (timerEnabled) Color(0xFF3A2630) else Lavender, contentColor = if (timerEnabled) Color(0xFFFFB9C5) else Color(0xFF181326))
                        ) { Text(if (timerEnabled) "Stop floating timer" else "Enable floating timer", fontWeight = FontWeight.Bold) }
                        if (!usageGranted || !overlayGranted) Text("Grant both permissions to enable the timer. Return here after each permission screen.", color = Color(0xFFFFC078), fontSize = 12.sp)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Most-used apps", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("Your usage so far today", color = Muted, fontSize = 13.sp)
                    }
                    TextButton(onClick = { if (usageGranted) apps = runCatching { UsageRepository.getTodayUsage(context) }.getOrDefault(emptyList()) }) { Text("Refresh") }
                }
            }
            if (!usageGranted) {
                item { EmptyState("Usage access needed", "Allow Usage Access above to see your app-by-app screen time.") }
            } else if (top.isEmpty()) {
                item { EmptyState("Your dashboard is ready", "Use your phone for a little while, then return here or tap Refresh to load today's usage.") }
            } else {
                items(top, key = { it.packageName }) { app ->
                    AppUsageRow(app = app, fraction = app.durationMs.toFloat() / maxUsage.toFloat())
                }
            }
            item {
                Text("PRIVACY", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text("Usage information is read from Android on this device. TimeAware does not require an account or send usage history to a server.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF191B25)).padding(12.dp)) {
        Text(label, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
        Spacer(Modifier.height(5.dp))
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PermissionItem(title: String, subtitle: String, granted: Boolean, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (granted) Color(0xFF173C31) else Color(0xFF30283A)), contentAlignment = Alignment.Center) {
            Text(if (granted) "✓" else "!", color = if (granted) Mint else Lavender, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(if (granted) "Permission enabled" else subtitle, color = if (granted) Mint else Muted, fontSize = 11.sp)
        }
        if (!granted) TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun AppUsageRow(app: UsageRepository.AppUsage, fraction: Float) {
    val hueColor = listOf(Color(0xFFB9A5FF), Color(0xFF7FE0B1), Color(0xFFFFC078), Color(0xFF85C8FF))[app.label.hashCode().absoluteValueSafe() % 4]
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(hueColor.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                Text(app.label.take(1).uppercase(), color = hueColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = hueColor, trackColor = Color(0xFF30323E))
            }
            Spacer(Modifier.width(12.dp))
            Text(formatDuration(app.durationMs), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(5.dp))
            Text(body, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

private fun Int.absoluteValueSafe(): Int = if (this == Int.MIN_VALUE) 0 else kotlin.math.abs(this)

private fun formatDuration(ms: Long): String {
    val mins = (ms.coerceAtLeast(0L) / 60_000L)
    val hours = mins / 60L
    val minutes = mins % 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}
