@file:OptIn(ExperimentalMaterial3Api::class)
package com.focusguard.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlin.math.roundToInt

private val Blue = Color(0xFF0A84FF)
private val Red = Color(0xFFFF3B30)

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); enableEdgeToEdge()
        setContent { Theme { Root() } }
    }
}

@Composable
fun Theme(content: @Composable () -> Unit) {
    val d = isSystemInDarkTheme()
    val cs = if (d) darkColorScheme(primary = Blue, background = Color.Black, surface = Color(0xFF1C1C1E),
        onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color(0xFF8E8E93), surfaceVariant = Color(0xFF2C2C2E), error = Red)
    else lightColorScheme(primary = Blue, background = Color(0xFFF2F2F7), surface = Color.White,
        onBackground = Color.Black, onSurface = Color.Black, onSurfaceVariant = Color(0xFF8E8E93), surfaceVariant = Color(0xFFE5E5EA), error = Red)
    MaterialTheme(colorScheme = cs, content = content)
}

@Composable
fun Root() {
    val c = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf(listOf<AppInfo>()) }
    var usage by remember { mutableStateOf(mapOf<String, Long>()) }
    var limits by remember { mutableStateOf(Store.all(c)) }
    var perm by remember { mutableStateOf(false to false) }
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppInfo?>(null) }

    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        apps = withContext(Dispatchers.Default) { loadApps(c) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val u = hasUsage(c)
            perm = u to Settings.canDrawOverlays(c)
            if (u) usage = withContext(Dispatchers.Default) { Usage.today(c) }
            limits = Store.all(c)
            delay(3000)
        }
    }
    LaunchedEffect(perm) { if (perm.first && perm.second) c.startForegroundService(Intent(c, MonitorService::class.java)) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), tonalElevation = 0.dp) {
            listOf(Icons.Default.Home to R.string.today, Icons.Default.Lock to R.string.limits, Icons.Default.Settings to R.string.setup)
                .forEachIndexed { i, (ic, label) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(ic, null) }, label = { Text(stringResource(label), fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Blue, selectedTextColor = Blue, indicatorColor = Color.Transparent))
                }
        }
    }) { pad ->
        val by = apps.associateBy { it.pkg }
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 56.dp, 16.dp, pad.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                0 -> {
                    val list = usage.entries.filter { by.containsKey(it.key) && it.value > 60_000 }.sortedByDescending { it.value }.take(20)
                    val total = list.sumOf { it.value }; val max = (list.firstOrNull()?.value ?: 1L).toFloat()
                    item { Title(stringResource(R.string.today)) }
                    item { Card { Column(Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.total), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        Text(fmt(total), fontSize = 46.sp, fontWeight = FontWeight.Bold)
                    } } }
                    item { Header(stringResource(R.string.most_used)) }
                    item { Card { Column { list.forEach { (p, ms) ->
                        val a = by[p]!!
                        Row2(a, fmt(ms), ms / max, if (limits.containsKey(p)) Red else Blue) { editing = a }
                    } } } }
                }
                1 -> {
                    item { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Title(stringResource(R.string.limits))
                        TextButton({ picking = true }) { Text("+ " + stringResource(R.string.add_limit), color = Blue, fontSize = 16.sp) }
                    } }
                    if (limits.isEmpty()) item { Text(stringResource(R.string.no_limits), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    else item { Card { Column { limits.entries.mapNotNull { (p, m) -> by[p]?.let { it to m } }.forEach { (a, m) ->
                        val used = usage[a.pkg] ?: 0L
                        Row2(a, "${fmt(used)} / ${fmt(m * 60_000L)}", used / (m * 60_000f), if (used >= m * 60_000L) Red else Blue) { editing = a }
                    } } } }
                }
                else -> {
                    item { Title(stringResource(R.string.setup)) }
                    item { Card { Column {
                        PermRow(stringResource(R.string.usage_access), stringResource(R.string.usage_desc), perm.first) {
                            c.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                        PermRow(stringResource(R.string.overlay), stringResource(R.string.overlay_desc), perm.second) {
                            c.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}"))) }
                    } } }
                }
            }
        }
    }

    if (picking) ModalBottomSheet({ picking = false }, containerColor = MaterialTheme.colorScheme.background) {
        var q by remember { mutableStateOf("") }
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.search)) },
                singleLine = true, shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(apps.filter { it.name.contains(q, true) }) { a ->
                    Row(Modifier.fillMaxWidth().clickable { picking = false; editing = a }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(a); Spacer(Modifier.width(12.dp)); Text(a.name, fontSize = 17.sp)
                    }
                }
            }
        }
    }
    editing?.let { a ->
        ModalBottomSheet({ editing = null }, containerColor = MaterialTheme.colorScheme.background) {
            val cur = limits[a.pkg] ?: 0
            var m by remember { mutableFloatStateOf(if (cur > 0) cur.toFloat() else 60f) }
            Column(Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(a); Spacer(Modifier.width(12.dp)); Text(a.name, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Text(stringResource(R.string.daily_limit), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt(m.toLong() * 60_000), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Slider(m, { m = (it / 5).roundToInt() * 5f }, valueRange = 5f..480f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 120, 180).forEach { v -> AssistChip({ m = v.toFloat() }, { Text(fmt(v * 60_000L)) }) }
                }
                Button({ Store.set(c, a.pkg, m.toInt()); limits = Store.all(c); editing = null },
                    Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.save), fontSize = 17.sp) }
                if (cur > 0) TextButton({ Store.set(c, a.pkg, 0); limits = Store.all(c); editing = null }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.remove), color = Red) }
            }
        }
    }
}

@Composable private fun Title(t: String) = Text(t, fontSize = 34.sp, fontWeight = FontWeight.Bold)
@Composable private fun Header(t: String) = Text(t.uppercase(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
@Composable private fun Card(content: @Composable () -> Unit) =
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), MaterialTheme.colorScheme.surface, content = content)
@Composable private fun Icon(a: AppInfo) =
    Image(a.icon, null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))

@Composable
private fun Row2(a: AppInfo, right: String, progress: Float, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(a); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(a.name, fontSize = 17.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text(right, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator({ progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@Composable
private fun PermRow(title: String, desc: String, ok: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontSize = 17.sp); Text(desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (ok) Text("✓ " + stringResource(R.string.granted), color = Color(0xFF34C759))
        else Button(onGrant, shape = RoundedCornerShape(20.dp)) { Text(stringResource(R.string.grant)) }
    }
}
