package com.yunsmall.flyingmouse

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
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
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
    var port by rememberSaveable { mutableStateOf("3240") }
    var isBusy by remember { mutableStateOf(false) }
    var motionEnabled by rememberSaveable { mutableStateOf(true) }
    var sensitivity by rememberSaveable { mutableFloatStateOf(2.0f) }
    var showKeyboard by rememberSaveable { mutableStateOf(false) }
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

    val orientation = LocalConfiguration.current.orientation
    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE

    // Shared toggle callback
    val onToggleServer: () -> Unit = {
        val svc = service
        if (svc != null) {
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
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (showKeyboard && serverRunning) {
                CompactTopBar(serverRunning, isBusy, port, ipAddress.value,
                    onToggleServer, { port = it },
                    onCloseKeyboard = { showKeyboard = false },
                    motionEnabled, { motionEnabled = it })
            } else {
                ServerCard(serverRunning, isBusy, port, ipAddress.value,
                    onPortChange = { port = it },
                    onToggleServer = onToggleServer)
            }

            if (serverRunning) {
                if (!showKeyboard) {
                    ConnectionHint(ipAddress.value, port)
                    MotionControlBar(motionEnabled, { motionEnabled = it },
                        sensitivity, { sensitivity = it },
                        showKeyboard, { showKeyboard = !showKeyboard })
                }
                if (showKeyboard) {
                    Spacer(Modifier.weight(1f))
                    KeyboardSheet(Modifier.fillMaxWidth())
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        DPad(
                            modifier = Modifier.size(if (isLandscape) 150.dp else 200.dp),
                            onKeyDown = { FlyingMouseNative.pressKey(it) },
                            onKeyUp = { FlyingMouseNative.releaseKey(it) })
                        Spacer(Modifier.height(if (isLandscape) 4.dp else 16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 12.dp else 24.dp)) {
                            MouseButton("L", leftPressed, Color(0xFF4CAF50),
                                onPress = { leftPressed = it; FlyingMouseNative.setLeftButton(it) },
                                modifier = Modifier.width(80.dp).height(if (isLandscape) 36.dp else 48.dp))
                            MouseButton("R", rightPressed, Color(0xFFE53935),
                                onPress = { rightPressed = it; FlyingMouseNative.setRightButton(it) },
                                modifier = Modifier.width(80.dp).height(if (isLandscape) 36.dp else 48.dp))
                        }
                        Spacer(Modifier.height(if (isLandscape) 2.dp else 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MouseButton("M", middlePressed, Color(0xFFFFB300),
                                onPress = { middlePressed = it; FlyingMouseNative.setMiddleButton(it) },
                                modifier = Modifier.size(if (isLandscape) 32.dp else 40.dp))
                            ScrollArea(
                                modifier = Modifier.width(if (isLandscape) 24.dp else 32.dp)
                                    .height(if (isLandscape) 36.dp else 48.dp),
                                onScroll = { FlyingMouseNative.sendMouseWheel(it.coerceIn(-127, 127)) })
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Start the server to begin\ncontrolling your PC",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
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
        Row(Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape)
                .background(if (serverRunning) Color(0xFF4CAF50) else Color(0xFF757575)))
            Column(Modifier.weight(1f)) {
                Text(if (serverRunning) "Server Running" else "Server Stopped",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (serverRunning && ipAddress != null)
                    Text("$ipAddress:${port.toIntOrNull() ?: 3240}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(value = port, onValueChange = { onPortChange(it.filter { c -> c.isDigit() }) },
                label = { Text("Port") }, modifier = Modifier.width(80.dp), singleLine = true,
                enabled = !serverRunning && !isBusy)
            Button(onClick = onToggleServer, enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (serverRunning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary)) {
                if (isBusy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (serverRunning) "Stopping…" else "Starting…")
                } else Text(if (serverRunning) "Stop" else "Start")
            }
        }
    }
}

@Composable
fun CompactTopBar(serverRunning: Boolean, isBusy: Boolean, port: String, ipAddress: String?,
                  onToggleServer: () -> Unit, onPortChange: (String) -> Unit,
                  onCloseKeyboard: () -> Unit, motionEnabled: Boolean,
                  onMotionToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape)
            .background(if (serverRunning) Color(0xFF4CAF50) else Color(0xFF757575)))
        OutlinedTextField(value = port,
            onValueChange = { onPortChange(it.filter { c -> c.isDigit() }) },
            label = { Text("Port", fontSize = 10.sp) },
            modifier = Modifier.width(64.dp), singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
            enabled = !serverRunning && !isBusy)
        Button(onClick = onToggleServer, enabled = !isBusy,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (serverRunning) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary)) {
            if (isBusy) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
            } else Text(if (serverRunning) "Stop" else "Start", fontSize = 12.sp)
        }
        Switch(checked = motionEnabled, onCheckedChange = onMotionToggle,
            modifier = Modifier.height(24.dp))
        TextButton(onClick = onCloseKeyboard, modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Text("⌨▲", fontSize = 14.sp)
        }
    }
}

@Composable
fun ConnectionHint(ip: String?, port: String) {
    Card(Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
        Text("Connect: usbip attach -r ${ip ?: "IP"} -b 1-1",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun MouseButton(label: String, pressed: Boolean, color: Color,
                onPress: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(16.dp))
        .background(if (pressed) color else color.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
        .pointerInput(Unit) {
            detectTapGestures(onPress = { onPress(true); tryAwaitRelease(); onPress(false) })
        },
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            color = if (pressed) Color.White else color)
    }
}

@Composable
fun DPad(modifier: Modifier = Modifier, onKeyDown: (Int) -> Unit, onKeyUp: (Int) -> Unit) {
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val arrowBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    Box(modifier = modifier.clip(CircleShape).background(bg)) {
        val arrow = Modifier.fillMaxWidth(0.33f).fillMaxHeight(0.33f)
        DPadButton("▲", DPadKeys.UP, arrowBg,
            Modifier.align(Alignment.TopCenter).then(arrow), onKeyDown, onKeyUp)
        DPadButton("▼", DPadKeys.DOWN, arrowBg,
            Modifier.align(Alignment.BottomCenter).then(arrow), onKeyDown, onKeyUp)
        DPadButton("◀", DPadKeys.LEFT, arrowBg,
            Modifier.align(Alignment.CenterStart).then(arrow), onKeyDown, onKeyUp)
        DPadButton("▶", DPadKeys.RIGHT, arrowBg,
            Modifier.align(Alignment.CenterEnd).then(arrow), onKeyDown, onKeyUp)
        // OK button
        var okPressed by remember { mutableStateOf(false) }
        val okBg = if (okPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                   else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        Box(Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape)
            .background(okBg)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    okPressed = true; onKeyDown(0x28)
                    tryAwaitRelease()
                    okPressed = false; onKeyUp(0x28)
                })
            }, contentAlignment = Alignment.Center) {
            Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DPadButton(label: String, keycode: Int, color: Color, modifier: Modifier,
                       onKeyDown: (Int) -> Unit, onKeyUp: (Int) -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val bgColor = if (pressed) color.copy(alpha = 0.35f) else color
    Box(modifier = modifier.background(bgColor).pointerInput(keycode) {
            detectTapGestures(onPress = {
                pressed = true; onKeyDown(keycode)
                tryAwaitRelease()
                pressed = false; onKeyUp(keycode)
            })
        }, contentAlignment = Alignment.Center) {
        Text(label, fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ScrollArea(modifier: Modifier = Modifier, onScroll: (Int) -> Unit) {
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) {
                detectTapGestures(onPress = { onScroll(3); tryAwaitRelease() })
            }, contentAlignment = Alignment.Center) {
            Text("▲", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) {
                detectTapGestures(onPress = { onScroll(-3); tryAwaitRelease() })
            }, contentAlignment = Alignment.Center) {
            Text("▼", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MotionControlBar(enabled: Boolean, onToggle: (Boolean) -> Unit,
                     sensitivity: Float, onSensitivityChange: (Float) -> Unit,
                     keyboardOn: Boolean, onKeyboardToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Switch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.height(32.dp))
        Text("Motion", style = MaterialTheme.typography.labelSmall)
        if (enabled) {
            Text("${"%.1f".format(sensitivity)}", style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
            Slider(value = sensitivity, onValueChange = onSensitivityChange,
                valueRange = 1.0f..5.0f, modifier = Modifier.weight(1f))
        }
        TextButton(onClick = onKeyboardToggle, modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Text(if (keyboardOn) "⌨▲" else "⌨", fontSize = 14.sp)
        }
    }
}

@Composable
fun KeyboardSheet(modifier: Modifier = Modifier) {
    val H = FlyingMouseNative.HID
    val L = H.LETTERS

    var shiftOn by remember { mutableStateOf(false) }
    var ctrlOn by remember { mutableStateOf(false) }
    var altOn by remember { mutableStateOf(false) }
    var winOn by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    Row(modifier = modifier.clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surface)
        .height(IntrinsicSize.Max)) {
        Box(Modifier.weight(1f).verticalScroll(scrollState)) {
            Column(Modifier.fillMaxWidth().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                KeyboardRow(listOf(
                    "1" to H.NUMBERS[0], "2" to H.NUMBERS[1], "3" to H.NUMBERS[2],
                    "4" to H.NUMBERS[3], "5" to H.NUMBERS[4], "6" to H.NUMBERS[5],
                    "7" to H.NUMBERS[6], "8" to H.NUMBERS[7], "9" to H.NUMBERS[8], "0" to H.NUMBERS[9]))
                KeyboardRow(listOf(
                    "Q" to L[16], "W" to L[22], "E" to L[4], "R" to L[17], "T" to L[19],
                    "Y" to L[24], "U" to L[20], "I" to L[8], "O" to L[14], "P" to L[15]))
                KeyboardRow(listOf(
                    "A" to L[0], "S" to L[18], "D" to L[3], "F" to L[5],
                    "G" to L[6], "H" to L[7], "J" to L[9], "K" to L[10], "L" to L[11]))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    KeyButton("⇧", Modifier.weight(1f), active = shiftOn,
                        onDown = { shiftOn = !shiftOn
                            FlyingMouseNative.nativeSetModifier(H.MOD_LEFT_SHIFT, shiftOn) }, onUp = {})
                    listOf("Z" to L[25], "X" to L[23], "C" to L[2], "V" to L[21],
                        "B" to L[1], "N" to L[13], "M" to L[12]).forEach { (l, kc) ->
                        KeyButton(l, Modifier.weight(1f),
                            onDown = { FlyingMouseNative.pressKey(kc) },
                            onUp = { FlyingMouseNative.releaseKey(kc) }) }
                    KeyButton("⌫", Modifier.weight(1.3f),
                        onDown = { FlyingMouseNative.pressKey(H.KEY_BACKSPACE) },
                        onUp = { FlyingMouseNative.releaseKey(H.KEY_BACKSPACE) })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    KeyButton("Ctrl", Modifier.weight(1f), active = ctrlOn,
                        onDown = { ctrlOn = !ctrlOn
                            FlyingMouseNative.nativeSetModifier(H.MOD_LEFT_CTRL, ctrlOn) }, onUp = {})
                    KeyButton("Alt", Modifier.weight(1f), active = altOn,
                        onDown = { altOn = !altOn
                            FlyingMouseNative.nativeSetModifier(H.MOD_LEFT_ALT, altOn) }, onUp = {})
                    KeyButton("Space", Modifier.weight(4f),
                        onDown = { FlyingMouseNative.pressKey(H.KEY_SPACE) },
                        onUp = { FlyingMouseNative.releaseKey(H.KEY_SPACE) })
                    KeyButton("Win", Modifier.weight(1f), active = winOn,
                        onDown = { winOn = !winOn
                            FlyingMouseNative.nativeSetModifier(H.MOD_LEFT_GUI, winOn) }, onUp = {})
                    KeyButton("↵", Modifier.weight(1f),
                        onDown = { FlyingMouseNative.pressKey(H.KEY_ENTER) },
                        onUp = { FlyingMouseNative.releaseKey(H.KEY_ENTER) })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    listOf("←" to H.KEY_LEFT, "↑" to H.KEY_UP,
                        "↓" to H.KEY_DOWN, "→" to H.KEY_RIGHT).forEach { (l, kc) ->
                        KeyButton(l, Modifier.weight(1f),
                            onDown = { FlyingMouseNative.pressKey(kc) },
                            onUp = { FlyingMouseNative.releaseKey(kc) }) }
                }
                KeyboardRow(listOf(
                    "-" to H.KEY_MINUS, "=" to H.KEY_EQUAL,
                    "[" to H.KEY_LEFTBRACE, "]" to H.KEY_RIGHTBRACE, "\\" to H.KEY_BACKSLASH,
                    ";" to H.KEY_SEMICOLON, "'" to H.KEY_QUOTE,
                    "," to H.KEY_COMMA, "." to H.KEY_PERIOD, "/" to H.KEY_SLASH))
                KeyboardRow(listOf(
                    "Esc" to H.KEY_ESC, "Tab" to H.KEY_TAB,
                    "Ins" to H.KEY_INSERT, "Del" to H.KEY_DELETE,
                    "Home" to H.KEY_HOME, "End" to H.KEY_END,
                    "PgUp" to H.KEY_PAGEUP, "PgDn" to H.KEY_PAGEDOWN))
            }
        }
        // Drag area — fills Row height (determined by keyboard)
        Box(Modifier.width(48.dp).fillMaxHeight()
            .verticalScroll(scrollState)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center) {
            Text("↕", fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun KeyboardRow(keys: List<Pair<String, Int>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        keys.forEach { (l, kc) ->
            KeyButton(l, Modifier.weight(1f),
                onDown = { FlyingMouseNative.pressKey(kc) },
                onUp = { FlyingMouseNative.releaseKey(kc) })
        }
    }
}

@Composable
private fun KeyButton(label: String, modifier: Modifier = Modifier,
                      onDown: () -> Unit, onUp: () -> Unit, active: Boolean = false) {
    var pressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    val bg = when {
        pressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(modifier = modifier
        .padding(1.dp)
        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        .background(bg, shape)
        .pointerInput(label) {
            detectTapGestures(onPress = {
                pressed = true; onDown()
                tryAwaitRelease()
                pressed = false; onUp()
            })
        }
        .padding(vertical = 4.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            color = if (pressed || active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
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
        if (active && gyroSensor != null)
            sensorManager.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
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
                if (!addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false)
                    return addr.hostAddress
            }
        }
    } catch (_: Exception) {}
    return null
}

// region Previews

@Preview(showBackground = true) @Composable
fun DPadPreview() { FlyingMouseTheme { DPad(Modifier.size(250.dp), {}, {}) } }

@Preview(showBackground = true) @Composable
fun ServerCardPreview() {
    FlyingMouseTheme { ServerCard(false, false, "3240", null, {}, {}) }
}

// endregion
