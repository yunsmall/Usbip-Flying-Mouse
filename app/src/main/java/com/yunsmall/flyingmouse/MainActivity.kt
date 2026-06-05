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
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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

object AppSettings {
    private const val PREFS = "flying_mouse_settings"
    private const val KEY_SENSITIVITY = "sensitivity"
    private const val KEY_TAP_TIMEOUT = "tap_drag_timeout"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_DEADZONE = "deadzone"
    private const val KEY_TAP_TOLERANCE = "tap_tolerance"
    private const val KEY_CLICK_DURATION = "click_duration"
    private const val KEY_PORT = "port"
    private const val KEY_SCROLL_SPEED = "scroll_speed"

    data class Settings(
        val sensitivity: Float, val tapTimeout: Int, val language: String,
        val deadzone: Float = 2f, val tapTolerance: Float = 60f,
        val clickDuration: Int = 60, val port: Int = 3240, val scrollSpeed: Int = 1)

    fun load(context: Context): Settings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(
            sensitivity = p.getFloat(KEY_SENSITIVITY, 2.0f),
            tapTimeout = p.getInt(KEY_TAP_TIMEOUT, 400),
            language = p.getString(KEY_LANGUAGE, "zh") ?: "zh",
            deadzone = p.getFloat(KEY_DEADZONE, 2f),
            tapTolerance = p.getFloat(KEY_TAP_TOLERANCE, 60f),
            clickDuration = p.getInt(KEY_CLICK_DURATION, 60),
            port = p.getInt(KEY_PORT, 3240),
            scrollSpeed = p.getInt(KEY_SCROLL_SPEED, 1))
    }

    fun save(context: Context, s: Settings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_SENSITIVITY, s.sensitivity)
            .putInt(KEY_TAP_TIMEOUT, s.tapTimeout)
            .putString(KEY_LANGUAGE, s.language)
            .putFloat(KEY_DEADZONE, s.deadzone)
            .putFloat(KEY_TAP_TOLERANCE, s.tapTolerance)
            .putInt(KEY_CLICK_DURATION, s.clickDuration)
            .putInt(KEY_PORT, s.port)
            .putInt(KEY_SCROLL_SPEED, s.scrollSpeed).apply()
    }

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

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences("flying_mouse_settings", Context.MODE_PRIVATE)
            .getString("language", "zh") ?: "zh"
        val locale = if (lang == "en") java.util.Locale("en") else java.util.Locale("zh")
        java.util.Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
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
    val initSettings = remember { AppSettings.load(context) }
    var serverRunning by remember { mutableStateOf(false) }
    var port by rememberSaveable { mutableStateOf(initSettings.port.toString()) }
    var isBusy by remember { mutableStateOf(false) }
    var motionEnabled by rememberSaveable { mutableStateOf(true) }
    var sensitivity by rememberSaveable { mutableFloatStateOf(initSettings.sensitivity) }
    var tapDragTimeout by rememberSaveable { mutableIntStateOf(initSettings.tapTimeout) }
    var deadzone by rememberSaveable { mutableFloatStateOf(initSettings.deadzone) }
    var tapTolerance by rememberSaveable { mutableFloatStateOf(initSettings.tapTolerance) }
    var clickDuration by rememberSaveable { mutableIntStateOf(initSettings.clickDuration) }
    var scrollSpeed by rememberSaveable { mutableIntStateOf(initSettings.scrollSpeed) }
    var showKeyboard by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val ipAddress = remember { mutableStateOf<String?>(null) }
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }
    var middlePressed by remember { mutableStateOf(false) }
    val currentLang = initSettings.language

    // persist settings when changed
    val portInt = port.toIntOrNull() ?: 3240
    LaunchedEffect(sensitivity, tapDragTimeout, deadzone, tapTolerance, clickDuration, port, scrollSpeed) {
        AppSettings.save(context,
            AppSettings.Settings(sensitivity, tapDragTimeout, currentLang, deadzone, tapTolerance, clickDuration, portInt, scrollSpeed))
    }

    MotionSensorEffect(serverRunning && motionEnabled && !showKeyboard, sensitivity)

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
                        else Toast.makeText(context, context.getString(R.string.failed_to_start), Toast.LENGTH_SHORT).show()
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
                    onSettingsClick = { showSettings = true })
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
                        showKeyboard, { showKeyboard = !showKeyboard },
                        onSettingsClick = { showSettings = true })
                }
                if (showKeyboard) {
                    if (isLandscape) {
                        KeyboardSheet(Modifier.fillMaxWidth().weight(1f))
                    } else {
                        TouchpadArea(Modifier.fillMaxWidth().weight(1f), tapDragTimeout, deadzone, tapTolerance, clickDuration, scrollSpeed)
                        KeyboardSheet(Modifier.fillMaxWidth())
                    }
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
                                speed = scrollSpeed,
                                onScroll = { FlyingMouseNative.sendMouseWheel(it.coerceIn(-127, 127)) })
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.start_prompt),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        }

        if (showSettings) {
            SettingsDialog(
                sensitivity = sensitivity,
                tapDragTimeout = tapDragTimeout,
                deadzone = deadzone,
                tapTolerance = tapTolerance,
                clickDuration = clickDuration,
                port = port,
                scrollSpeed = scrollSpeed,
                onSensitivityChange = { sensitivity = it },
                onTapDragTimeoutChange = { tapDragTimeout = it },
                onDeadzoneChange = { deadzone = it },
                onTapToleranceChange = { tapTolerance = it },
                onClickDurationChange = { clickDuration = it },
                onPortChange = { port = it },
                onScrollSpeedChange = { scrollSpeed = it },
                onDismiss = { showSettings = false })
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
                Text(if (serverRunning) stringResource(R.string.server_running_title)
                     else stringResource(R.string.server_stopped_title),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (serverRunning && ipAddress != null)
                    Text("$ipAddress:${port.toIntOrNull() ?: 3240}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(value = port, onValueChange = { onPortChange(it.filter { c -> c.isDigit() }) },
                label = { Text(stringResource(R.string.port)) }, modifier = Modifier.width(80.dp), singleLine = true,
                enabled = !serverRunning && !isBusy)
            Button(onClick = onToggleServer, enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (serverRunning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary)) {
                if (isBusy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (serverRunning) stringResource(R.string.stopping) else stringResource(R.string.starting))
                } else Text(if (serverRunning) stringResource(R.string.stop) else stringResource(R.string.start))
            }
        }
    }
}

@Composable
fun SettingsDialog(sensitivity: Float, tapDragTimeout: Int,
                   deadzone: Float, tapTolerance: Float, clickDuration: Int,
                   port: String, scrollSpeed: Int,
                   onSensitivityChange: (Float) -> Unit,
                   onTapDragTimeoutChange: (Int) -> Unit,
                   onDeadzoneChange: (Float) -> Unit,
                   onTapToleranceChange: (Float) -> Unit,
                   onClickDurationChange: (Int) -> Unit,
                   onPortChange: (String) -> Unit,
                   onScrollSpeedChange: (Int) -> Unit,
                   onDismiss: () -> Unit) {
    val context = LocalContext.current
    val curLang = context.resources.configuration.locales[0].language
    val isZh = curLang == "zh"

    fun switchLang(code: String) {
        AppSettings.save(context, AppSettings.Settings(
            sensitivity, tapDragTimeout, code,
            deadzone, tapTolerance, clickDuration, port.toIntOrNull() ?: 3240, scrollSpeed))
        (context as? android.app.Activity)?.recreate()
    }

    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // language
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.language) + ":",
                        style = MaterialTheme.typography.bodyMedium)
                    FilterChip(selected = isZh,
                        onClick = { if (!isZh) switchLang("zh") },
                        label = { Text("中文") })
                    FilterChip(selected = !isZh,
                        onClick = { if (isZh) switchLang("en") },
                        label = { Text("English") })
                }
                HorizontalDivider()
                // port
                OutlinedTextField(value = port,
                    onValueChange = { onPortChange(it.filter { c -> c.isDigit() }) },
                    label = { Text(stringResource(R.string.port)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                // sensitivity
                Text("${stringResource(R.string.sensitivity)}: ${"%.1f".format(sensitivity)}",
                    style = MaterialTheme.typography.bodyMedium)
                Slider(value = sensitivity, onValueChange = onSensitivityChange,
                    valueRange = 1.0f..5.0f, steps = 7)
                // tap drag timeout
                Text("${stringResource(R.string.tap_drag_timeout)}: ${tapDragTimeout}ms",
                    style = MaterialTheme.typography.bodyMedium)
                Slider(value = tapDragTimeout.toFloat(),
                    onValueChange = { onTapDragTimeoutChange(it.toInt()) },
                    valueRange = 100f..1000f, steps = 8)
                // deadzone
                Text("${stringResource(R.string.deadzone)}: ${"%.1f".format(deadzone)}px",
                    style = MaterialTheme.typography.bodyMedium)
                Slider(value = deadzone,
                    onValueChange = onDeadzoneChange,
                    valueRange = 0f..10f, steps = 9)
                // tap tolerance
                Text("${stringResource(R.string.tap_tolerance)}: ${tapTolerance.toInt()}px",
                    style = MaterialTheme.typography.bodyMedium)
                Slider(value = tapTolerance,
                    onValueChange = onTapToleranceChange,
                    valueRange = 20f..150f, steps = 12)
                // click duration
                Text("${stringResource(R.string.click_duration)}: ${clickDuration}ms",
                    style = MaterialTheme.typography.bodyMedium)
                Slider(value = clickDuration.toFloat(),
                    onValueChange = { onClickDurationChange(it.toInt()) },
                    valueRange = 20f..200f, steps = 8)
                // scroll speed
                Text("${stringResource(R.string.scroll_speed)}: $scrollSpeed",
                    style = MaterialTheme.typography.bodyMedium)
                Slider(value = scrollSpeed.toFloat(),
                    onValueChange = { onScrollSpeedChange(it.toInt()) },
                    valueRange = 1f..10f, steps = 8)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        })
}

@Composable
fun CompactTopBar(serverRunning: Boolean, isBusy: Boolean, port: String, ipAddress: String?,
                  onToggleServer: () -> Unit, onPortChange: (String) -> Unit,
                  onCloseKeyboard: () -> Unit, onSettingsClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape)
            .background(if (serverRunning) Color(0xFF4CAF50) else Color(0xFF757575)))
        OutlinedTextField(value = port,
            onValueChange = { onPortChange(it.filter { c -> c.isDigit() }) },
            label = { Text(stringResource(R.string.port), fontSize = 10.sp) },
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
            } else Text(if (serverRunning) stringResource(R.string.stop) else stringResource(R.string.start), fontSize = 12.sp)
        }
        TextButton(onClick = onCloseKeyboard, modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Text("⌨▲", fontSize = 14.sp)
        }
        TextButton(onClick = onSettingsClick, modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Text("⚙", fontSize = 16.sp)
        }
    }
}

@Composable
fun ConnectionHint(ip: String?, port: String) {
    Card(Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))) {
        Text(stringResource(R.string.server_address_hint, ip ?: "IP"),
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
fun ScrollArea(modifier: Modifier = Modifier, speed: Int = 3, onScroll: (Int) -> Unit) {
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) {
                detectTapGestures(onPress = { onScroll(speed); tryAwaitRelease() })
            }, contentAlignment = Alignment.Center) {
            Text("▲", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) {
                detectTapGestures(onPress = { onScroll(-speed); tryAwaitRelease() })
            }, contentAlignment = Alignment.Center) {
            Text("▼", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TouchpadArea(modifier: Modifier = Modifier, tapDragTimeout: Int = 400,
                 deadzone: Float = 2f, tapTolerance: Float = 60f, clickDuration: Int = 60,
                 scrollSpeed: Int = 1) {
    val scope = rememberCoroutineScope()
    val dragState = remember {
        object {
            var pending = false
            var tapX = 0f; var tapY = 0f
            var timer: kotlinx.coroutines.Job? = null
        }
    }

    fun fireClick() {
        scope.launch {
            FlyingMouseNative.setLeftButton(true)
            kotlinx.coroutines.delay(clickDuration.toLong())
            FlyingMouseNative.setLeftButton(false)
        }
    }

    Box(modifier = modifier
        .padding(vertical = 4.dp)
        .clip(RoundedCornerShape(12.dp))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                var pointerCount = 1
                var secondId: androidx.compose.ui.input.pointer.PointerId? = null
                var thirdId: androidx.compose.ui.input.pointer.PointerId? = null
                var lastX = down.position.x
                var lastY = down.position.y
                var totalDist = 0f
                var moved = false

                // check if this touch continues a previous tap
                var isDrag = false
                if (dragState.pending) {
                    dragState.timer?.cancel()
                    dragState.pending = false
                    val dx = down.position.x - dragState.tapX
                    val dy = down.position.y - dragState.tapY
                    if (kotlin.math.abs(dx) < tapTolerance && kotlin.math.abs(dy) < tapTolerance) {
                        // same position → drag continuation, cancel the pending click
                        isDrag = true
                        FlyingMouseNative.setLeftButton(true)
                    } else {
                        // different position → fire the old click, this is a new gesture
                        fireClick()
                    }
                }

                do {
                    val event = awaitPointerEvent()
                    val changes = event.changes

                    for (c in changes) {
                        if (c.id != down.id && c.pressed) {
                            when {
                                secondId == null -> { secondId = c.id; pointerCount = 2 }
                                thirdId == null -> { thirdId = c.id; pointerCount = 3 }
                            }
                        }
                    }

                    if (pointerCount >= 2 && isDrag && !moved) {
                        FlyingMouseNative.setLeftButton(false)
                        isDrag = false
                    }

                    val first = changes.find { it.id == down.id }
                    if (first != null && first.pressed) {
                        val dx = first.position.x - lastX
                        val dy = first.position.y - lastY
                        totalDist += kotlin.math.abs(dx) + kotlin.math.abs(dy)
                        lastX = first.position.x
                        lastY = first.position.y

                        if (totalDist > deadzone) {
                            moved = true
                            if (pointerCount >= 3) {
                                FlyingMouseNative.sendMouseWheel((dy.toInt() * scrollSpeed).coerceIn(-127, 127))
                            } else {
                                FlyingMouseNative.sendMouseMove(dx.toInt(), dy.toInt())
                            }
                        }
                        first.consume()
                    }

                    for (c in changes) {
                        if (c.id != down.id && c.pressed) c.consume()
                    }
                } while (changes.any { it.pressed })

                // cancel any stale pending tap
                dragState.timer?.cancel()
                dragState.pending = false

                if (isDrag) {
                    FlyingMouseNative.setLeftButton(false)
                }
                if (!moved) {
                    when {
                        pointerCount >= 3 -> {
                            scope.launch {
                                FlyingMouseNative.setMiddleButton(true)
                                kotlinx.coroutines.delay(clickDuration.toLong())
                                FlyingMouseNative.setMiddleButton(false)
                            }
                        }
                        pointerCount >= 2 -> {
                            scope.launch {
                                FlyingMouseNative.setRightButton(true)
                                kotlinx.coroutines.delay(clickDuration.toLong())
                                FlyingMouseNative.setRightButton(false)
                            }
                        }
                        else -> {
                            // delay click — if user touches again soon, it becomes a drag instead
                            dragState.pending = true
                            dragState.tapX = down.position.x
                            dragState.tapY = down.position.y
                            dragState.timer = scope.launch {
                                kotlinx.coroutines.delay(tapDragTimeout.toLong())
                                if (dragState.pending) {
                                    dragState.pending = false
                                    fireClick()
                                }
                            }
                        }
                    }
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.touchpad), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            Text(stringResource(R.string.touchpad_hint), fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
        }
    }
}

@Composable
fun MotionControlBar(enabled: Boolean, onToggle: (Boolean) -> Unit,
                     sensitivity: Float, onSensitivityChange: (Float) -> Unit,
                     keyboardOn: Boolean, onKeyboardToggle: () -> Unit,
                     onSettingsClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Switch(checked = enabled, onCheckedChange = onToggle, modifier = Modifier.height(32.dp))
        Text(stringResource(R.string.motion), style = MaterialTheme.typography.labelSmall)
        Text("${"%.1f".format(sensitivity)}", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        Slider(value = sensitivity, onValueChange = onSensitivityChange,
            valueRange = 1.0f..5.0f, modifier = Modifier.weight(1f))
        TextButton(onClick = onKeyboardToggle, modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Text(if (keyboardOn) "⌨▲" else "⌨", fontSize = 14.sp)
        }
        TextButton(onClick = onSettingsClick, modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Text("⚙", fontSize = 16.sp)
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

    val up = shiftOn
    val numKeys = (if (up) listOf("!","@","#","$","%","^","&","*","(",")")
                  else listOf("1","2","3","4","5","6","7","8","9","0"))
        .zip(H.NUMBERS)
    val letterRows = listOf(
        listOf("q","w","e","r","t","y","u","i","o","p").zip(listOf(16,22,4,17,19,24,20,8,14,15).map { L[it] }),
        listOf("a","s","d","f","g","h","j","k","l").zip(listOf(0,18,3,5,6,7,9,10,11).map { L[it] }),
        listOf("z","x","c","v","b","n","m").zip(listOf(25,23,2,21,1,13,12).map { L[it] }))
    val symKeys = (if (up) listOf("_","+","{","}","|",":","\"","<",">","?")
                   else listOf("-","=","[","]","\\",";","'",",",".","/"))
        .zip(listOf(H.KEY_MINUS, H.KEY_EQUAL, H.KEY_LEFTBRACE, H.KEY_RIGHTBRACE, H.KEY_BACKSLASH,
                    H.KEY_SEMICOLON, H.KEY_QUOTE, H.KEY_COMMA, H.KEY_PERIOD, H.KEY_SLASH))
    fun dl(l: String) = if (up) l.uppercase() else l

    val scrollState = rememberScrollState()
    Row(modifier = modifier.clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surface)
        .height(IntrinsicSize.Max)) {
        Box(Modifier.weight(1f).verticalScroll(scrollState)) {
            Column(Modifier.fillMaxWidth().padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                KeyboardRow(numKeys)
                KeyboardRow(letterRows[0].map { (l, kc) -> dl(l) to kc })
                KeyboardRow(letterRows[1].map { (l, kc) -> dl(l) to kc })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    KeyButton("⇧", Modifier.weight(1f), active = shiftOn,
                        onDown = { shiftOn = !shiftOn
                            FlyingMouseNative.nativeSetModifier(H.MOD_LEFT_SHIFT, shiftOn) }, onUp = {})
                    letterRows[2].forEach { (l, kc) ->
                        KeyButton(dl(l), Modifier.weight(1f),
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
                KeyboardRow(symKeys)
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
        .padding(vertical = 8.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 11.sp, softWrap = false, fontWeight = FontWeight.Medium,
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
