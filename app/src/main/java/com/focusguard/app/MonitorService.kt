package com.focusguard.app

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.*
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class MonitorService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel("fg", "FocusGuard", NotificationManager.IMPORTANCE_MIN))
        val n = NotificationCompat.Builder(this, "fg").setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(getString(R.string.app_name)).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        scope.coroutineContext.cancelChildren()
        scope.launch { loop() }
        return START_STICKY
    }

    private suspend fun loop() {
        val usm = getSystemService(UsageStatsManager::class.java)
        var cur: String? = null
        var last = System.currentTimeMillis() - 3_600_000
        val e = UsageEvents.Event()
        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            val ev = usm.queryEvents(last, now)
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                if (e.eventType == 1) cur = e.packageName else if (e.eventType == 2 && e.packageName == cur) cur = null
            }
            last = now
            val p = cur
            if (p != null && p != packageName) {
                val lim = Store.limit(this, p)
                if (lim > 0 && (Usage.today(this)[p] ?: 0L) >= lim * 60_000L)
                    startActivity(Intent(this, LockActivity::class.java).putExtra("pkg", p)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
            delay(1500)
        }
    }

    override fun onDestroy() { scope.cancel() }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try { c.startForegroundService(Intent(c, MonitorService::class.java)) } catch (_: Exception) {}
    }
}

class LockActivity : ComponentActivity() {
    private fun home() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
    @Deprecated("Deprecated in Java") override fun onBackPressed() = home()
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val name = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
        setContent { MaterialTheme { Lock(name) { home() } } }
    }
}

@Composable
private fun Lock(name: String, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black).padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("⏳", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.time_up), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.time_up_msg, name), color = Color(0xFF8E8E93), fontSize = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onHome, modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))) { Text(stringResource(R.string.go_home), fontSize = 17.sp) }
    }
}
