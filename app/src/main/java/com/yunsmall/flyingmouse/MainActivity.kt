package com.yunsmall.flyingmouse

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunsmall.flyingmouse.ui.theme.FlyingMouseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DPadKeys {
    const val UP = 0x52
    const val DOWN = 0x51
    const val LEFT = 0x50
    const val RIGHT = 0x4F
}

class MainActivity : ComponentActivity() {

    private val serviceState = mutableStateOf<FlyingMouseService?>(null)
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            serviceState.value = (binder as FlyingMouseService.LocalBinder).getService()
            serviceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            serviceState.value = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startForegroundService(Intent(this, FlyingMouseService::class.java))
        bindService(Intent(this, FlyingMouseService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        setContent { FlyingMouseTheme { FlyingMouseApp(serviceState.value) } }
    }

    override fun onDestroy() {
        if (serviceBound) { unbindService(serviceConnection); serviceBound = false }
        super.onDestroy()
    }
}

@Composable
fun FlyingMouseApp(service: FlyingMouseService?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var serverRunning by remember { mutableStateOf(false) }
    var port by remember { mutableStateOf("3240") }
    var isBusy by remember { mutableStateOf(false) }
    var motionEnabled by remember { mutableStateOf(true) }
    var sensitivity by remember { mutableFloatStateOf(2.0f) }
    val ipAddress = remember { mutableStateOf<String?>(null) }
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }
    var middlePressed by remember { mutableStateOf(false) }

    MotionSensorEffect(serverRunning && motionEnabled, sensitivity)

    LaunchedEffect(service) {
        service?.let {
            serverRunning = it.serverRunning
            port = it.port.toString()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ServerCard(
                serverRunning = serverRunning, isBusy = isBusy, port = port,
                ipAddress = ipAddress.value,
                onPortChange = { port = it },
                onToggleServer = {
                    val svc = service ?: return@ServerCard
                    isBusy = true
                    scope.launch(Dispatchers.IO) {
                        if (serverRunning) {
                            svc.stopServer()
                            withContext(Dispatchers.Main) { serverRunning = false }
                        } else {
                            val p = port.toIntOrNull() ?: 3240
                            val ok = svc.startServer(p)
                            withContext(Dispatchers.Main) {
                                if (ok) { serverRunning = true; ipAddress.value = getIpAddress() }
                                else Toast.makeText(context, "Failed to start", Toast.LENGTH_SHORT).show()
                            }
                        }
                        withContext(Dispatchers.Main) { isBusy = false }
                    }
                }
            )

            if (serverRunning) {
                ConnectionHint(ipAddress.value, port)
                MotionControlBar(motionEnabled, { motionEnabled = it }, sensitivity, { sensitivity = it })
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    DPad(
                        modifier = Modifier.size(200.dp),
                        onKeyDown = { FlyingMouseNative.pressKey(it) },
                        onKeyUp = { FlyingMouseNative.releaseKey(it) })
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        MouseButton("L", leftPressed, Color(0xFF4CAF50),
                            onPress = { leftPressed = it; FlyingMouseNative.setLeftButton(it) },
                            modifier = Modifier.width(80.dp).height(48.dp))
                        MouseButton("R", rightPressed, Color(0xFFE53935),
                            onPress = { rightPressed = it; FlyingMouseNative.setRightButton(it) },
                            modifier = Modifier.width(80.dp).height(48.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MouseButton("M", middlePressed, Color(0xFFFFB300),
                            onPress = { middlePressed = it; FlyingMouseNative.setMiddleButton(it) },
                            modifier = Modifier.size(40.dp))
                        ScrollArea(
                            modifier = Modifier.width(32.dp).height(48.dp),
                            onScroll = { FlyingMouseNative.sendMouseWheel(it.coerceIn(-127, 127)) })
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Start the server to begin\ncontrolling your PC",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// region Composables

@Composable
fun ServerCard(serverRunning: Boolean, isBusy: Boolean, port: String, ipAddress: String?,
               onPortChange: (String) -> Unit, onToggleServer: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape)
                .background(if (serverRunning) Color(0xFF4CAF50) else Color(0xFF757575)))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (serverRunning) "Server Running" else "Server Stopped",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (serverRunning && ipAddress != null)
                    Text("$ipAddress:${port.toIntOrNull() ?: 3240}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(value = port, onValueChange = { onPortChange(it.filter { c -> c.isDigit() }) },
                label = { Text("Port") }, modifier = Modifier.width(80.dp), singleLine = true,
                enabled = !serverRunning && !isBusy)
            Button(onClick = onToggleServer, enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (serverRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) {
                if (isBusy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (serverRunning) "Stopping…" else "Starting…")
                } else Text(if (serverRunning) "Stop" else "Start")
            }
        }
    }
}

@Composable
fun ConnectionHint(ip: String?, port: String) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
        Text("Connect: usbip attach -r ${ip ?: "IP"} -b 1-1", modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun MouseButton(label: String, pressed: Boolean, color: Color, onPress: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(16.dp))
        .background(if (pressed) color else color.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
        .pointerInput(Unit) { detectTapGestures(onPress = { onPress(true); tryAwaitRelease(); onPress(false) }) },
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (pressed) Color.White else color)
    }
}

@Composable
fun DPad(modifier: Modifier = Modifier, onKeyDown: (Int) -> Unit, onKeyUp: (Int) -> Unit) {
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val arrowBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    Box(modifier = modifier.clip(CircleShape).background(bg)) {
        val arrow = Modifier.fillMaxWidth(0.33f).fillMaxHeight(0.33f)
        DPadButton("▲", DPadKeys.UP, arrowBg, Modifier.align(Alignment.TopCenter).then(arrow), onKeyDown, onKeyUp)
        DPadButton("▼", DPadKeys.DOWN, arrowBg, Modifier.align(Alignment.BottomCenter).then(arrow), onKeyDown, onKeyUp)
        DPadButton("◀", DPadKeys.LEFT, arrowBg, Modifier.align(Alignment.CenterStart).then(arrow), onKeyDown, onKeyUp)
        DPadButton("▶", DPadKeys.RIGHT, arrowBg, Modifier.align(Alignment.CenterEnd).then(arrow), onKeyDown, onKeyUp)
        // OK button center
        Box(
            modifier = Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                .pointerInput(Unit) { detectTapGestures(onPress = { onKeyDown(0x28); tryAwaitRelease(); onKeyUp(0x28) }) },
            contentAlignment = Alignment.Center
        ) { Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun DPadButton(label: String, keycode: Int, color: Color, modifier: Modifier,
                       onKeyDown: (Int) -> Unit, onKeyUp: (Int) -> Unit) {
    Box(modifier = modifier.background(color)
        .pointerInput(keycode) { detectTapGestures(onPress = { onKeyDown(keycode); tryAwaitRelease(); onKeyUp(keycode) }) },
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ScrollArea(modifier: Modifier = Modifier, onScroll: (Int) -> Unit) {
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) { detectTapGestures(onPress = { onScroll(3); tryAwaitRelease() }) },
            contentAlignment = Alignment.Center) { Text("▲", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) { detectTapGestures(onPress = { onScroll(-3); tryAwaitRelease() }) },
            contentAlignment = Alignment.Center) { Text("▼", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun MotionControlBar(enabled: Boolean, onToggle: (Boolean) -> Unit, sensitivity: Float, onSensitivityChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.height(32.dp))
        Text("Motion", style = MaterialTheme.typography.labelSmall)
        if (enabled) {
            Text("${"%.1f".format(sensitivity)}", style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
            Slider(value = sensitivity, onValueChange = onSensitivityChange, valueRange = 1.0f..5.0f,
                modifier = Modifier.weight(1f))
        }
    }
}

// endregion

@Composable
fun MotionSensorEffect(active: Boolean, sensitivity: Float) {
    val context = LocalContext.current
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED)
    val currentSensitivity by rememberUpdatedState(sensitivity)
    val listener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gx = Math.toDegrees(event.values[0].toDouble()).toFloat()
                val gz = Math.toDegrees(event.values[2].toDouble()).toFloat()
                val s = currentSensitivity * currentSensitivity * 0.05f
                val dx = (-gz * s).toInt()
                val dy = (-gx * s).toInt()
                if (dx != 0 || dy != 0) FlyingMouseNative.sendMouseMove(dx, dy)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }
    DisposableEffect(active, gyroSensor) {
        if (active && gyroSensor != null) sensorManager.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        else sensorManager.unregisterListener(listener)
        onDispose { sensorManager.unregisterListener(listener) }
    }
}

fun getIpAddress(): String? {
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val ni = interfaces.nextElement()
            if (ni.isLoopback || !ni.isUp) continue
            val addrs = ni.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (!addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false) return addr.hostAddress
            }
        }
    } catch (_: Exception) {}
    return null
}

// region Previews

@Preview(showBackground = true) @Composable
fun DPadPreview() { FlyingMouseTheme { DPad(Modifier.size(250.dp), {}, {}) } }

@Preview(showBackground = true) @Composable
fun ServerCardPreview() { FlyingMouseTheme { ServerCard(false, false, "3240", null, {}, {}) } }

// endregion
