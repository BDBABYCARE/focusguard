package com.focusguard.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.Calendar

data class AppInfo(val pkg: String, val name: String, val icon: ImageBitmap)

object Store {
    private fun p(c: Context) = c.getSharedPreferences("fg", 0)
    fun limit(c: Context, pkg: String) = p(c).getInt("l_$pkg", 0)
    fun set(c: Context, pkg: String, min: Int) =
        p(c).edit().apply { if (min <= 0) remove("l_$pkg") else putInt("l_$pkg", min) }.apply()
    fun all(c: Context): Map<String, Int> =
        p(c).all.filterKeys { it.startsWith("l_") }.mapKeys { it.key.removePrefix("l_") }.mapValues { it.value as Int }
}

object Usage {
    /** Foreground milliseconds per package since local midnight. */
    fun today(c: Context): Map<String, Long> {
        val usm = c.getSystemService(UsageStatsManager::class.java)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(cal.timeInMillis, now)
        val e = UsageEvents.Event()
        val start = HashMap<String, Long>(); val tot = HashMap<String, Long>()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e); val p = e.packageName
            when (e.eventType) {
                1 -> start.putIfAbsent(p, e.timeStamp)
                2 -> start.remove(p)?.let { tot[p] = (tot[p] ?: 0L) + (e.timeStamp - it) }
            }
        }
        start.forEach { (p, s) -> tot[p] = (tot[p] ?: 0L) + (now - s) }
        return tot
    }
}

fun loadApps(c: Context): List<AppInfo> {
    val pm = c.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.distinct()
        .filter { it != c.packageName }
        .map {
            val ai = pm.getApplicationInfo(it, 0)
            AppInfo(it, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap())
        }.sortedBy { it.name.lowercase() }
}

fun fmt(ms: Long): String { val m = ms / 60000; return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }

@Suppress("DEPRECATION")
fun hasUsage(c: Context): Boolean {
    val a = c.getSystemService(AppOpsManager::class.java)
    val m = if (Build.VERSION.SDK_INT >= 29) a.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
    else a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
    return m == AppOpsManager.MODE_ALLOWED
}
