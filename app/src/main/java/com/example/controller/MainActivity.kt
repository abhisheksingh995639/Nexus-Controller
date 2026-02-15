package com.example.controller

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import java.util.HashSet

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.os.VibrationAttributes
import android.media.AudioAttributes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.zIndex
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import org.json.JSONObject
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

// AppColors moved to ControllerShared.kt

// Data class to hold layout config with MutableState for reactivity
// CompConfig moved to ControllerShared.kt

class MainActivity : ComponentActivity(), SensorEventListener {
    private val networkController = NetworkController()
    private lateinit var prefs: SharedPreferences
    private lateinit var profilesPrefs: SharedPreferences

    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor? = null
    
    // Gyro State
    private var gyroRoll by mutableIntStateOf(0)
    private var gyroPitch by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("layout_prefs", MODE_PRIVATE)
        profilesPrefs = getSharedPreferences("profiles_list", MODE_PRIVATE)
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // Immersive Sticky Mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
             androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
             val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
             insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
             insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
             @Suppress("DEPRECATION")
             window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        setContent {
            var isConnected by remember { mutableStateOf(false) }
            var shouldAutoReconnect by remember { mutableStateOf(true) }
            // Removed raw isDarkTheme, computed below
            var showMenu by remember { mutableStateOf(false) }
            var currentMode by remember { mutableStateOf(0) } // 0=Controller, 1=Trackpad
            var showSettings by remember { mutableStateOf(false) }
            var showHelp by remember { mutableStateOf(false) }
            var showAbout by remember { mutableStateOf(false) }
            var globalToastMessage by remember { mutableStateOf<String?>(null) }

            // Settings State
            var themeMode by remember { mutableStateOf(prefs.getString("theme_mode", "Dark") ?: "Dark") }
            // val isDarkTheme = themeMode != "Light" // Logic moved to composable
            
            var keepScreenOn by remember { mutableStateOf(prefs.getBoolean("keep_screen_on", true)) }
            var hapticEnabled by remember { mutableStateOf(prefs.getBoolean("haptic_enabled", true)) }
            var hapticStrength by remember { mutableFloatStateOf(prefs.getFloat("haptic_strength", 0.85f)) }
            var gyroEnabled by remember { mutableStateOf(prefs.getBoolean("gyro_enabled", false)) }
            var gyroSensitivity by remember { mutableFloatStateOf(prefs.getFloat("gyro_sensitivity", 0.4f)) }
            var touchVibration by remember { mutableStateOf(prefs.getBoolean("touch_vibration", true)) }
            var autoReconnect by remember { mutableStateOf(prefs.getBoolean("auto_reconnect", true)) }
            var deviceName by remember { mutableStateOf(prefs.getString("device_name", "Player 1") ?: "Player 1") }
            var touchSensitivity by remember { mutableFloatStateOf(prefs.getFloat("touch_sensitivity", 1.0f)) }
            
            // Screen On Logic
            LaunchedEffect(keepScreenOn) {
                if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            val context = LocalContext.current
            // Vibrator Ref
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            LaunchedEffect(Unit) {
                networkController.onStateChanged = { state ->
                    isConnected = state == NetworkController.State.CONNECTED
                    if (!isConnected) {
                        try { vibrator.cancel() } catch(e:Exception){}
                    }
                }
                networkController.onRumble = { l, s ->
                    // Rumble callback from PC (0-255)
                    // Map to Amplitude (1-255)
                    val strength = kotlin.math.max(l, s)
                    if (strength > 0 && hapticEnabled) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                               val attrs = VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_MEDIA).build()
                               // Reduced duration to 200ms for responsiveness
                               val scaledStrength = (strength * hapticStrength).toInt().coerceIn(1, 255)
                               vibrator.vibrate(VibrationEffect.createOneShot(200, scaledStrength), attrs)
                           } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                               val scaledStrength = (strength * hapticStrength).toInt().coerceAtLeast(1)
                               vibrator.vibrate(VibrationEffect.createOneShot(200, scaledStrength))
                           } else {
                               @Suppress("DEPRECATION")
                               vibrator.vibrate(200)
                           }
                        } catch(e: Exception){}
                    } else {
                        vibrator.cancel()
                    }
                }
                networkController.onError = { msg ->
                     (context as? android.app.Activity)?.runOnUiThread {
                         val userMsg = when {
                             msg.contains("failed to connect", ignoreCase = true) -> 
                                 "Can't find PC. Check if server is running and firewall is off."
                             msg.contains("Connection refused", ignoreCase = true) ->
                                 "Server refused connection. Ensure the PC app is open."
                             msg.contains("timeout", ignoreCase = true) ->
                                 "Connection timed out. Check your IP Address."
                             msg.contains("Network is unreachable", ignoreCase = true) ->
                                 "No network. Check your Wi-Fi or USB connection."
                             msg.contains("EOF", ignoreCase = true) || msg.contains("Connection Lost", ignoreCase = true) ->
                                 "Disconnected. Server app was closed."
                             msg.contains("address already in use", ignoreCase = true) ->
                                 "Port 6000 is busy. Please restart the app."
                             else -> "Connection Error: $msg"
                         }
                         globalToastMessage = userMsg
                     }
                }
                networkController.connect()
            }
            
            var lastIp by remember { mutableStateOf(prefs.getString("last_ip", "") ?: "") }
            
            // Re-connect loop
            LaunchedEffect(lastIp, autoReconnect) {
                 while(true) {
                     kotlinx.coroutines.delay(3000)
                     if(!isConnected && autoReconnect) {
                         // Crucial: Use lastIp for auto-reconnection
                         networkController.connect(if(lastIp.isBlank()) null else lastIp)
                     }
                 }
            }
            val configs = remember { mutableStateMapOf<String, CompConfig>() }
            var showLayoutManager by remember { mutableStateOf(false) }
            var currentProfileName by remember { mutableStateOf(prefs.getString("active_profile", "default") ?: "default") }
            // Reactive state for profiles
            var profileList by remember { mutableStateOf(getProfileList()) }

            LaunchedEffect(currentProfileName) {
                loadLayout(configs, currentProfileName)
                prefs.edit().putString("active_profile", currentProfileName).apply()
            }
            
            var showConnectionDialog by remember { mutableStateOf(false) }
            var showKeyboardDialog by remember { mutableStateOf(false) }
            var triggerReset by remember { mutableStateOf(false) }

            // Gyro Offsets (Moved here for scope)
            var gyroRollOffset by remember { mutableIntStateOf(0) }
            var gyroPitchOffset by remember { mutableIntStateOf(0) }

            // Layout Editor State
            var showLayoutEditor by remember { mutableStateOf(false) }
            var isCreatingNew by remember { mutableStateOf(false) }
            var showNameDialog by remember { mutableStateOf(false) }
            var tempConfigsForSave = remember { mutableMapOf<String, CompConfig>() }

            PSControllerScreen(
                isConnected, 
                showConnectionDialog,
                currentMode,
                onToggleMenu = { showMenu = true },
                configs,
                themeMode,
                gyroRoll,
                gyroPitch,
                onToggleTheme = { 
                    themeMode = when(themeMode) {
                        "Dark" -> "Neon"
                        "Neon" -> "Light"
                        else -> "Dark"
                    }
                    prefs.edit().putString("theme_mode", themeMode).apply()
                },
                onSave = { saveLayout(configs, currentProfileName) },
                onOpenProfiles = { showLayoutManager = true },
                onOpenConnection = { showConnectionDialog = true },
                onCloseConnection = { showConnectionDialog = false },
                onOpenKeyboard = { showKeyboardDialog = true },
                onAddButton = {
                    // Functionality moved to Layout Editor
                },
                onInputChanged = { bl, bh, lx, ly, rx, ry, lt, rt ->
                    // Apply Gyro Sensitivity and Enable check
                    // Apply Calibration Offsets
                    val gRoll = if (gyroEnabled) ((gyroRoll - gyroRollOffset) * gyroSensitivity).toInt() else 0
                    val gPitch = if (gyroEnabled) ((gyroPitch - gyroPitchOffset) * gyroSensitivity).toInt() else 0
                    networkController.sendInput(lx, ly, rx, ry, bl, bh, lt, rt, gRoll, gPitch)
                },
                onMouseMove = { dx, dy, l, r ->
                     networkController.sendMouse(dx, dy, l, r, touchSensitivity)
                },
                onScroll = { dx, dy ->
                     networkController.sendScroll(dx, dy, touchSensitivity)
                },
                onSendText = { text ->
                     networkController.sendText(text)
                },
                triggerReset = triggerReset,
                onResetDone = { triggerReset = false },
                touchVibration = touchVibration,
                hapticStrength = hapticStrength
            )

            // Glass/Blur Backdrop when Sidebar is open
            if (showMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .blur(8.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showMenu = false }
                        .zIndex(15f)
                )
            }

            StitchSidebar(
                isVisible = showMenu,
                currentMode = currentMode,
                onModeSelect = { mode -> 
                    currentMode = mode
                    showMenu = false
                    // Reset other overlays just in case
                    showSettings = false
                    showHelp = false
                    showAbout = false
                },
                onDisconnect = {
                    shouldAutoReconnect = false
                    networkController.disconnect()
                    showMenu = false
                    showConnectionDialog = false
                },
                onDismiss = { showMenu = false },
                onSettingsClick = { showMenu = false; showSettings = true },
                onHelpClick = { showMenu = false; showHelp = true }, 
                onAboutClick = { showMenu = false; showAbout = true },
                onLayoutsClick = { showMenu = false; showLayoutManager = true },
                themeMode = themeMode
            )
            
            if (showHelp) {
                 Box(modifier = Modifier.zIndex(100f).fillMaxSize()) {
                     HelpScreen(onBack = { showHelp = false }, themeMode = themeMode)
                 }
            }

            if (showAbout) {
                 Box(modifier = Modifier.zIndex(100f).fillMaxSize()) {
                     AboutScreen(onBack = { showAbout = false }, themeMode = themeMode)
                 }
            }
            

            // Gyro Offsets moved up


            SettingsScreen(
                isVisible = showSettings,
                onBack = { showSettings = false; saveLayout(configs, currentProfileName) },
                state = SettingsState(
                    themeMode = themeMode,
                    keepScreenOn = keepScreenOn,
                    hapticEnabled = hapticEnabled,
                    hapticStrength = hapticStrength,
                    gyroEnabled = gyroEnabled,
                    gyroSensitivity = gyroSensitivity,
                    touchVibration = touchVibration,
                    autoReconnect = autoReconnect,
                    deviceName = deviceName,
                    touchSensitivity = touchSensitivity
                ),
                onThemeChange = { mode -> 
                    themeMode = mode
                    prefs.edit().putString("theme_mode", mode).apply()
                },
                onScreenOnToggle = { 
                    keepScreenOn = it
                    prefs.edit().putBoolean("keep_screen_on", it).apply()
                },
                onHapticToggle = { 
                    hapticEnabled = it
                    prefs.edit().putBoolean("haptic_enabled", it).apply()
                },
                onHapticStrengthChange = { 
                    hapticStrength = it
                    prefs.edit().putFloat("haptic_strength", it).apply()
                },
                onGyroToggle = { 
                    gyroEnabled = it
                    prefs.edit().putBoolean("gyro_enabled", it).apply()
                },
                onGyroSensitivityChange = { 
                    gyroSensitivity = it
                    prefs.edit().putFloat("gyro_sensitivity", it).apply()
                },
                onCalibrateGyro = { 
                    gyroRollOffset = gyroRoll 
                    gyroPitchOffset = gyroPitch
                    globalToastMessage = "Gyro Center Calibrated"
                },
                onTouchVibrationToggle = { 
                    touchVibration = it
                    prefs.edit().putBoolean("touch_vibration", it).apply()
                },
                onAutoReconnectToggle = {
                    autoReconnect = it
                    prefs.edit().putBoolean("auto_reconnect", it).apply()
                },
                onDeviceNameChange = {
                    deviceName = it
                    prefs.edit().putString("device_name", it).apply()
                },
                onTouchSensitivityChange = {
                    touchSensitivity = it
                    prefs.edit().putFloat("touch_sensitivity", it).apply()
                },
                onSave = { showSettings = false },
                onReset = {
                    // Reset to defaults
                    themeMode = "Dark"
                    keepScreenOn = true
                    hapticEnabled = true
                    hapticStrength = 0.85f
                    gyroEnabled = false
                    gyroSensitivity = 0.4f
                    touchVibration = true
                    autoReconnect = true
                    // deviceName = "Player 1" // Keep existing name
                    touchSensitivity = 1.0f
                    gyroRollOffset = 0
                    gyroPitchOffset = 0
                    
                    val editor = prefs.edit()
                    editor.putString("theme_mode", "Dark")
                    editor.putBoolean("keep_screen_on", true)
                    editor.putBoolean("haptic_enabled", true)
                    editor.putFloat("haptic_strength", 0.85f)
                    editor.putBoolean("gyro_enabled", false)
                    editor.putFloat("gyro_sensitivity", 0.4f)
                    editor.putBoolean("touch_vibration", true)
                    editor.putBoolean("auto_reconnect", true)
                    // editor.putString("device_name", "Player 1")
                    editor.putFloat("touch_sensitivity", 1.0f)
                    editor.apply()
                }
            )

            // Dialogs Overlay (Top Level)
             if (showConnectionDialog) {
                Box(Modifier.fillMaxSize().zIndex(1f)) {
                    StitchConnectionScreen(
                        currentIp = lastIp,
                        onDismiss = { showConnectionDialog = false },
                        onConnect = { ip ->
                            prefs.edit().putString("last_ip", ip).apply()
                            lastIp = ip
                            val target = if(ip.isBlank()) "127.0.0.1" else ip
                            shouldAutoReconnect = true
                            networkController.disconnect()
                            networkController.connect(target)
                            showConnectionDialog = false
                        },
                        onQrScan = {
                            val options = GmsBarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .enableAutoZoom()
                                .build()
                            val scanner = GmsBarcodeScanning.getClient(this@MainActivity, options)
                            scanner.startScan()
                                .addOnSuccessListener { barcode ->
                                    val rawValue: String? = barcode.rawValue
                                    if (!rawValue.isNullOrBlank()) {
                                        // Auto connect to the scanned value
                                        prefs.edit().putString("last_ip", rawValue).apply()
                                        shouldAutoReconnect = true
                                        networkController.disconnect()
                                        networkController.connect(rawValue)
                                        showConnectionDialog = false
                                    }
                                }
                                .addOnFailureListener { e ->
                                    globalToastMessage = "QR Scan failed. Please try again or enter IP manually."
                                    android.util.Log.e("QRScan", "Scan failed", e)
                                }
                        },
                        onStartDiscovery = { cb -> networkController.startDiscovery(this@MainActivity, cb) },
                        onStopDiscovery = { networkController.stopDiscovery() },
                        themeMode = themeMode
                    )
                }
            }
            
            if (showKeyboardDialog) {
                KeyboardDialog(
                    onDismiss = { showKeyboardDialog = false },
                    onSend = { text ->
                        networkController.sendText(text)
                        showKeyboardDialog = false
                    }
                )
            }

            // Removed duplicate triggerReset

            if (showLayoutManager) {
                Box(modifier = Modifier.zIndex(100f).fillMaxSize()) {
                    LayoutManagerScreen(
                        layouts = profileList,
                        activeProfile = currentProfileName,
                        onBack = { showLayoutManager = false },
                        onCreate = { _ ->
                            isCreatingNew = true
                            showLayoutEditor = true
                            showLayoutManager = false
                        },
                        onSelect = { name ->
                            currentProfileName = name
                            // loadLayout triggered by LaunchedEffect(currentProfileName)
                        },
                        onEdit = { name ->
                            // Load this profile first
                            currentProfileName = name
                            isCreatingNew = false
                            showLayoutEditor = true
                            showLayoutManager = false
                        },
                        onRename = { oldName, newName ->
                            val profiles = profilesPrefs.getStringSet("names", HashSet())?.toMutableSet()
                            if (profiles != null && profiles.contains(oldName) && !profiles.contains(newName)) {
                                profiles.remove(oldName)
                                profiles.add(newName)
                                profilesPrefs.edit().putStringSet("names", profiles).apply()
                                
                                // Move data
                                val oldJson = prefs.getString("layout_json_$oldName", "{}")
                                prefs.edit().putString("layout_json_$newName", oldJson).apply()
                                prefs.edit().remove("layout_json_$oldName").apply()
                                
                                if (currentProfileName == oldName) {
                                    currentProfileName = newName
                                }
                                profileList = getProfileList() // Update UI
                            }
                        },
                        onDelete = { name ->
                             deleteProfile(name)
                             if (currentProfileName == name) {
                                 // Fallback to first available or default
                                 val list = getProfileList()
                                 val next = if (list.isNotEmpty()) list.first() else "default"
                                 currentProfileName = next
                             }
                             profileList = getProfileList() // Update UI
                        },
                        themeMode = themeMode
                    )
                }
            }

            if (showLayoutEditor) {
                Box(Modifier.fillMaxSize().zIndex(200f)) {
                    LayoutEditorScreen(
                        initialConfigs = configs.toMap(),
                        onBack = { showLayoutEditor = false; isCreatingNew = false },
                        onSave = { edited ->
                            tempConfigsForSave.clear()
                            tempConfigsForSave.putAll(edited)
                            if (isCreatingNew) {
                                showNameDialog = true
                            } else {
                                saveLayout(edited, currentProfileName)
                                configs.clear()
                                configs.putAll(edited)
                                showLayoutEditor = false
                            }
                        },
                        themeMode = themeMode
                    )
                }
            }

            if (showNameDialog) {
                InputDialog(
                    title = "Name Your Layout",
                    initialValue = "",
                    isLight = themeMode == "Light",
                    onDismiss = { showNameDialog = false },
                    onConfirm = { name ->
                        saveLayout(tempConfigsForSave, name)
                        currentProfileName = name
                        profileList = getProfileList()
                        showNameDialog = false
                        showLayoutEditor = false
                        isCreatingNew = false
                    }
                )
            }
            
            GlobalToast(globalToastMessage) { globalToastMessage = null }
        }
    }

    private fun getProfileList(): List<String> {
        return profilesPrefs.getStringSet("names", emptySet())?.toList()?.sorted() ?: emptyList()
    }

    private fun deleteProfile(name: String) {
        if (name == "autosave") return
        val set = profilesPrefs.getStringSet("names", HashSet())?.toMutableSet()
        if (set != null && set.contains(name)) {
            set.remove(name)
            profilesPrefs.edit().putStringSet("names", set).apply()
            prefs.edit().remove("layout_json_$name").apply()
        }
    }

    private fun saveLayout(configs: Map<String, CompConfig>, profileName: String = "autosave") {
        try {
            val json = JSONObject()
            configs.forEach { (key, value) ->
                val compJson = JSONObject()
                compJson.put("x", value.x)
                compJson.put("y", value.y)
                compJson.put("s", value.scale)
                compJson.put("r", value.rotation)
                compJson.put("k", value.mappedKey)
                compJson.put("turbo", value.isTurbo)
                json.put(key, compJson)
            }
            // Save payload
            prefs.edit().putString("layout_json_$profileName", json.toString()).apply()
            
            // Add to list if custom
            if (profileName != "autosave") {
                val set = profilesPrefs.getStringSet("names", HashSet())?.toMutableSet() ?: HashSet()
                set.add(profileName)
                profilesPrefs.edit().putStringSet("names", set).apply()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun loadLayout(configs: SnapshotStateMap<String, CompConfig>, profileName: String = "autosave") {
        try {
            // Check autosave legacy
            val key = "layout_json_$profileName"
            // Migration for legacy "layout_json" to "layout_json_autosave"
            if (profileName == "autosave" && !prefs.contains(key) && prefs.contains("layout_json")) {
                 val old = prefs.getString("layout_json", null)
                 prefs.edit().putString(key, old).apply()
            }

            val jsonString = prefs.getString(key, null)
            
            val defaults = mapOf(
                "L1" to CompConfig(100f, 100f, 0.9f),
                "L2" to CompConfig(100f, 220f, 0.9f),
                "R1" to CompConfig(1720f, 100f, 0.9f),
                "R2" to CompConfig(1720f, 220f, 0.9f),
                
                "SHARE" to CompConfig(780f, 280f, 0.9f),
                "OPTIONS" to CompConfig(1140f, 280f, 0.9f),
                "PS" to CompConfig(920f, 320f, 1f),
                
                "L_STICK" to CompConfig(100f, 650f, 1.1f),
                "DPAD" to CompConfig(600f, 600f, 1.0f),
                "FACE" to CompConfig(1120f, 600f, 1.0f),
                "R_STICK" to CompConfig(1650f, 650f, 1.1f)
            )
    
            configs.clear()
            if (jsonString != null) {
                val json = JSONObject(jsonString)
                val keys = json.keys()
                while(keys.hasNext()) {
                    val k = keys.next()
                    val obj = json.getJSONObject(k)
                    configs[k] = CompConfig(
                        obj.getDouble("x").toFloat(),
                        obj.getDouble("y").toFloat(),
                        obj.optDouble("s", 1.0).toFloat(),
                        obj.optDouble("r", 0.0).toFloat(),
                        obj.optInt("k", 0),
                        obj.optBoolean("turbo", false)
                    )
                }
                // Restore missing defaults
                defaults.forEach { (k, v) ->
                    if (!configs.containsKey(k)) configs[k] = v
                }
            } else {
                configs.putAll(defaults)
            }
        } catch (e: Exception) {
            e.printStackTrace()
             configs.clear()
             configs["PS"] = CompConfig(900f, 500f)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        networkController.disconnect()
    }
    
    override fun onResume() {
        super.onResume()
        sensor?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
             val rotationMatrix = FloatArray(9)
             SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
             
             // Remap for Landscape (Side buttons up/down)
             // Standard remapping for activity in landscape
             val remappedMatrix = FloatArray(9)
             SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remappedMatrix)
             
             val orientation = FloatArray(3)
             SensorManager.getOrientation(remappedMatrix, orientation)
             
             // orientation[1] = Pitch, [2] = Roll
             // In remapped landscape:
             // Pitch (1) = Tilting screen towards/away (Y-axis for mouse)
             // Roll (2) = Tilting left/right (Steering) (X-axis for mouse)
             
             gyroPitch = (orientation[1] * 10000).toInt()
             gyroRoll = (orientation[2] * 10000).toInt()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}



@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PSControllerScreen(
    isConnected: Boolean, 
    showConnectionDialog: Boolean,
    currentMode: Int,
    onToggleMenu: () -> Unit,
    configs: SnapshotStateMap<String, CompConfig>,
    themeMode: String,
    gyroRoll: Int,
    gyroPitch: Int,
    onToggleTheme: () -> Unit,
    onSave: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenConnection: () -> Unit,
    onCloseConnection: () -> Unit,
    onOpenKeyboard: () -> Unit,
    onAddButton: () -> Unit,
    onInputChanged: (Int, Int, Int, Int, Int, Int, Int, Int) -> Unit,
    onMouseMove: (Float, Float, Boolean, Boolean) -> Unit,
    onScroll: (Float, Float) -> Unit,
    onSendText: (String) -> Unit,
    triggerReset: Boolean,
    onResetDone: () -> Unit,
    touchVibration: Boolean,
    hapticStrength: Float // New Param
) {
    val scope = rememberCoroutineScope()
    // capture latest values for the logic loop:
    val currentGyroRoll by rememberUpdatedState(gyroRoll)
    val currentGyroPitch by rememberUpdatedState(gyroPitch)
    val currentOnInputChanged by rememberUpdatedState(onInputChanged)
    var steeringCenter by remember { mutableIntStateOf(0) }

    // Input State
    var leftX by remember { mutableIntStateOf(127) }
    var leftY by remember { mutableIntStateOf(127) }
    var rightX by remember { mutableIntStateOf(127) }
    var rightY by remember { mutableIntStateOf(127) }
    var leftTrigger by remember { mutableIntStateOf(0) }
    var rightTrigger by remember { mutableIntStateOf(0) }
    
    val activeCustomKeys = remember { mutableStateListOf<Int>() }
    
    // Turbo & Button State Logic
    var turboPulse by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while(true) {
            kotlinx.coroutines.delay(50) // 20 clicks/sec approx
            turboPulse = !turboPulse
        }
    }
    
    // Map "ID:Sub" -> Mask
    val pressedLow = remember { mutableStateMapOf<String, Int>() }
    val pressedHigh = remember { mutableStateMapOf<String, Int>() }

    fun updateBtn(id: String, sub: String, mask: Int, isHigh: Boolean, pressed: Boolean) {
        val key = "$id:$sub"
        if (isHigh) {
            if (pressed) pressedHigh[key] = mask else pressedHigh.remove(key)
        } else {
            if (pressed) pressedLow[key] = mask else pressedLow.remove(key)
        }
    }
    
    var isEditMode by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var isMouseMode by remember { mutableStateOf(false) }
    var isGyroEnabled by remember { mutableStateOf(true) }
    
    // UI Modes and Menu
    // Moved to setContent scope for sidebar integration
    
    val context = LocalContext.current
    
    // Theme
    // Theme Logic
    data class ThemeColors(
        val bg: Color,
        val bgGradientStart: Color,
        val bgGradientEnd: Color,
        val componentBg: Color,
        val componentStroke: Color,
        val text: Color,
        val accent: Color
    )

    val currentTheme = when(themeMode) {
        "Light" -> ThemeColors(
            bg = AppColors.BackgroundLight,
            bgGradientStart = Color(0xFFf5f6f8),
            bgGradientEnd = Color(0xFFd1d5db),
            componentBg = Color(0xFFE0E0E0),
            componentStroke = Color(0xFFCCCCCC),
            text = Color(0xFF333333),
            accent = AppColors.Primary
        )
        "Neon" -> ThemeColors(
            bg = Color.Black,
            bgGradientStart = Color.Black,
            bgGradientEnd = Color(0xFF050505),
            componentBg = Color(0xFF111111),
            componentStroke = AppColors.NeonBlue,
            text = AppColors.NeonBlue,
            accent = AppColors.NeonBlue
        )
        else -> ThemeColors( // Dark
            bg = Color(0xFF151515),
            bgGradientStart = Color(0xFF1a202e),
            bgGradientEnd = Color(0xFF101622),
            componentBg = Color(0xFF333333),
            componentStroke = Color(0xFF222222),
            text = Color(0xFFAAAAAA),
            accent = AppColors.Primary
        )
    }

    val bgColor = currentTheme.bg
    val componentBg = currentTheme.componentBg
    val componentStroke = currentTheme.componentStroke
    val textColor = currentTheme.text

    

    fun vibrate() {
        if (!touchVibration || !isConnected) return
        try {
            // Scale amplitude (1-255) based on strength setting
            val amplitude = (255 * hapticStrength).toInt().coerceIn(1, 255)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                
                if (vibrator?.hasVibrator() == true) {
                     // Use USAGE_MEDIA to ensure it works even if system "Touch Feedback" is off
                     val attrs = VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_MEDIA).build()
                     vibrator.vibrate(VibrationEffect.createOneShot(40, amplitude), attrs)
                }
            } else {
                 val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                 if (vibrator?.hasVibrator() == true) {
                     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                         vibrator.vibrate(VibrationEffect.createOneShot(40, amplitude)) 
                     } else {
                         @Suppress("DEPRECATION")
                         vibrator.vibrate(40)
                     }
                 }
            }
        } catch (e: Exception) { }
    }

    LaunchedEffect(Unit) {
        while(true) {
            val bl = pressedLow.asSequence().filter { (k,_) ->
                 val id = k.split(":")[0]
                 val isTurbo = configs[id]?.isTurbo == true
                 (!isTurbo || turboPulse)
            }.fold(0) { acc, (_, m) -> acc or m }
            
            val bh = pressedHigh.asSequence().filter { (k,_) ->
                 val id = k.split(":")[0]
                 val isTurbo = configs[id]?.isTurbo == true
                 (!isTurbo || turboPulse)
            }.fold(0) { acc, (_, m) -> acc or m }
            
            var lx = leftX
            var ly = leftY
            
            if (currentMode == 2) {
                // Racing Wheel Logic
                // Use currentGyroRoll and apply center offset
                val rawVal = currentGyroRoll - steeringCenter
                
                val steer = ((rawVal / 4500f).coerceIn(-1f, 1f) * 127.5f + 127.5f).toInt()
                lx = steer
                ly = 127
            }
            
            val finalButtonsHigh = bh or (if (isMouseMode) 0x40 else 0) or (if (!isGyroEnabled) 0x80 else 0)
            currentOnInputChanged(bl, finalButtonsHigh, lx, ly, rightX, rightY, leftTrigger, rightTrigger)
            
            delay(15) // Limit update rate to ~66Hz to prevent game freeze/flood
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(currentTheme.bgGradientStart, currentTheme.bgGradientEnd, Color.Black),
                    center = Offset.Unspecified,
                    radius = 2000f
                )
            )
    ) {
        // Carbon Pattern Background (Only for Dark)
        if (themeMode == "Dark") {
            CarbonBackgroundPattern()
        }
        // Grid pattern removed as per user request
        // Stitch Header (Hidden in Trackpad Mode to avoid double hamburger and clutter)
        if (currentMode != 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .height(48.dp)
            ) {
                // Menu Button (Neumorphic Flat - Rebuilt as Box)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AppColors.Surface)
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                        .clickable { onToggleMenu() },
                    contentAlignment = Alignment.Center
                ) {
                     Text("☰", color = Color.White.copy(alpha = 0.8f), fontSize = 20.sp)
                }

                // Play/Edit/Connect Toggle Group (Neumorphic Pressed)
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .height(44.dp)
                        .background(AppColors.Surface.copy(alpha = 0.8f), RoundedCornerShape(50))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(50))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("PLAY", "EDIT", "CONNECT").forEach { item ->
                        val displayLabel = if (item == "EDIT" && isEditMode) "SAVE" else item
                        val isActive = when(item) {
                            "PLAY" -> !isEditMode && !showConnectionDialog
                            "EDIT" -> isEditMode
                            "CONNECT" -> showConnectionDialog
                            else -> false
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isActive) AppColors.Primary else Color.Transparent)
                                .clickable {
                                    when(item) {
                                        "PLAY" -> { if(isEditMode) onSave(); isEditMode = false; onCloseConnection() }
                                        "EDIT" -> { if(isEditMode) onSave(); isEditMode = !isEditMode; onCloseConnection() }
                                        "CONNECT" -> { onOpenConnection(); isEditMode = false }
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                displayLabel, 
                                color = if (isActive) Color.White else Color.White.copy(alpha = 0.4f), 
                                fontSize = 12.sp, 
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
        
        // MAIN CONTENT SWITCH
        if (currentMode == 0) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val w = with(density) { maxWidth.toPx() }
                val h = with(density) { maxHeight.toPx() }
                
                LaunchedEffect(triggerReset, configs.isEmpty()) {
                    if (triggerReset || (configs.isEmpty() && w > 0)) {
                        val newDefaults = mapOf(
                            "L1" to CompConfig(w * 0.08f, h * 0.12f, 0.9f),
                            "L2" to CompConfig(w * 0.08f, h * 0.25f, 0.9f),
                            "R1" to CompConfig(w * 0.82f, h * 0.12f, 0.9f),
                            "R2" to CompConfig(w * 0.82f, h * 0.25f, 0.9f),
                            
                            "SHARE" to CompConfig(w * 0.38f, h * 0.28f, 0.9f),
                            "OPTIONS" to CompConfig(w * 0.58f, h * 0.28f, 0.9f),
                            "PS" to CompConfig(w * 0.46f, h * 0.32f, 1.0f),
                            
                            "L_STICK" to CompConfig(w * 0.08f, h * 0.65f, 1.2f),
                            "DPAD" to CompConfig(w * 0.30f, h * 0.60f, 1.0f),
                            "FACE" to CompConfig(w * 0.55f, h * 0.60f, 1.0f),
                            "R_STICK" to CompConfig(w * 0.80f, h * 0.65f, 1.2f)
                        )
                        configs.clear()
                        configs.putAll(newDefaults)
                        onSave() 
                        onResetDone()
                    }
                }

                val getConf = { id: String -> configs[id] ?: CompConfig(0f, 0f) }

                if (isEditMode) EditorGridBackground(themeMode == "Light")

                // Mapped Components
            EditableComponent("L2", isEditMode, selectedId == "L2", getConf("L2"), { selectedId = it }) {
                PSTriggerShape("L2", Modifier, componentBg, textColor) { f -> leftTrigger = (f * 255).toInt().coerceIn(0, 255) }
            }
            EditableComponent("L1", isEditMode, selectedId == "L1", getConf("L1"), { selectedId = it }) {
                PSBumperShape("L1", Modifier, componentBg, textColor, 0x10, ::vibrate) { m, p -> updateBtn("L1","",m,false,p) }
            }
            EditableComponent("DPAD", isEditMode, selectedId == "DPAD", getConf("DPAD"), { selectedId = it }) {
                 PSDpadDetailed(componentBg, textColor) { dir, pressed -> 
                    val mask = when(dir) { 0->0x04; 1->0x08; 2->0x10; 3->0x20; else->0 }
                    updateBtn("DPAD", dir.toString(), mask, true, pressed)
                    if(pressed) vibrate()
                 }
            }
            EditableComponent("L_STICK", isEditMode, selectedId == "L_STICK", getConf("L_STICK"), { selectedId = it }) {
                 PSJoystickSimple("L", componentBg, componentStroke) { x, y ->
                    leftX = ((x + 1) * 127.5).toInt().coerceIn(0, 255)
                    leftY = ((y + 1) * 127.5).toInt().coerceIn(0, 255)
                }
            }
            EditableComponent("R2", isEditMode, selectedId == "R2", getConf("R2"), { selectedId = it }) {
                PSTriggerShape("R2", Modifier, componentBg, textColor) { f -> rightTrigger = (f * 255).toInt().coerceIn(0, 255) }
            }
            EditableComponent("R1", isEditMode, selectedId == "R1", getConf("R1"), { selectedId = it }) {
                PSBumperShape("R1", Modifier, componentBg, textColor, 0x20, ::vibrate) { m, p -> updateBtn("R1","",m,false,p) }
            }
            EditableComponent("FACE", isEditMode, selectedId == "FACE", getConf("FACE"), { selectedId = it }) {
                 PSFaceButtonsDetailed(componentBg) { b, p -> 
                     if(p) vibrate()
                     updateBtn("FACE", b.toString(), b, false, p)
                 }
            }
            EditableComponent("R_STICK", isEditMode, selectedId == "R_STICK", getConf("R_STICK"), { selectedId = it }) {
                 PSJoystickSimple("R", componentBg, componentStroke) { x, y ->
                    rightX = ((x + 1) * 127.5).toInt().coerceIn(0, 255)
                    rightY = ((y + 1) * 127.5).toInt().coerceIn(0, 255)
                }
            }
            EditableComponent("SHARE", isEditMode, selectedId == "SHARE", getConf("SHARE"), { selectedId = it }) {
                PSCenterButton("SHARE", Modifier, componentBg, 0x40, false, ::vibrate) { m, p -> updateBtn("SHARE","",m,false,p) }
            }
            EditableComponent("OPTIONS", isEditMode, selectedId == "OPTIONS", getConf("OPTIONS"), { selectedId = it }) {
                 PSCenterButton("OPTIONS", Modifier, componentBg, 0x80, false, ::vibrate) { m, p -> updateBtn("OPTIONS","",m,false,p) }
            }
                  // PS Home Button (Stitch Design)
                  EditableComponent("PS", isEditMode, selectedId=="PS", getConf("PS"), onSelect={ selectedId="PS" }) {
                     Box(
                         modifier = Modifier
                             .size(72.dp)
                             .clip(CircleShape)
                             .background(
                                 brush = Brush.linearGradient(
                                     colors = listOf(AppColors.SurfaceHighlight, AppColors.Surface),
                                     start = Offset(0f,0f), end = Offset(72f, 72f)
                                 )
                             )
                             .border(1.dp, Color.White.copy(alpha=0.1f), CircleShape)
                             .shadow(10.dp, CircleShape)
                             .pointerInput(Unit) { 
                                detectTapGestures(onPress = { 
                                    vibrate()
                                    updateBtn("PS", "", 0, false, true)
                                    tryAwaitRelease()
                                    updateBtn("PS", "", 0, false, false)
                                }) 
                             },
                         contentAlignment = Alignment.Center
                     ) {
                         // Green/Red Glow Ring based on connection
                         val glowColor = if (isConnected) Color(0xFF22C55E) else Color(0xFFEF4444)
                         Box(Modifier.fillMaxSize().background(glowColor.copy(alpha=0.1f), CircleShape))
                         
                         // Icon - Better Gamepad Silhouette
                         // Icon - Detailed Gamepad from Image
                         Canvas(modifier = Modifier.size(36.dp)) {
                            // Green if connected, Red if not
                            val c = if (isConnected) Color(0xFF22C55E) else Color(0xFFEF4444) 
                            val w = size.width
                            val h = size.height
                            val stroke = Stroke(width=2.5f, cap=StrokeCap.Round, join=StrokeJoin.Round)
                            val thinStroke = Stroke(width=1.5f, cap=StrokeCap.Round)
                            
                            // 1. Frame (Rounded Gamepad)
                            val p = Path().apply {
                                moveTo(w*0.2f, h*0.2f) // Top left
                                quadraticBezierTo(w*0.5f, h*0.15f, w*0.8f, h*0.2f) // Top arch
                                // Right shoulder rounded
                                quadraticBezierTo(w*0.95f, h*0.2f, w*0.95f, h*0.4f) 
                                // Right handle
                                lineTo(w*0.95f, h*0.6f)
                                quadraticBezierTo(w*0.95f, h*0.85f, w*0.75f, h*0.8f) // Right tip
                                quadraticBezierTo(w*0.65f, h*0.75f, w*0.6f, h*0.6f) // Right inner
                                // Bottom arch
                                quadraticBezierTo(w*0.5f, h*0.55f, w*0.4f, h*0.6f) 
                                // Left handle
                                quadraticBezierTo(w*0.35f, h*0.75f, w*0.25f, h*0.8f) // Left inner
                                quadraticBezierTo(w*0.05f, h*0.85f, w*0.05f, h*0.6f) // Left tip
                                lineTo(w*0.05f, h*0.4f)
                                // Left shoulder rounded
                                quadraticBezierTo(w*0.05f, h*0.2f, w*0.2f, h*0.2f)
                                close()
                            }
                            drawPath(p, c, style = stroke)

                            // 2. D-pad (Left) - Cross
                            val dpX = w * 0.3f
                            val dpY = h * 0.45f // Move up slightly
                            val dpS = w * 0.08f // stick length
                            // Vertical
                            drawLine(c, Offset(dpX, dpY - dpS), Offset(dpX, dpY + dpS), strokeWidth=2f, cap=StrokeCap.Round)
                            // Horizontal
                            drawLine(c, Offset(dpX - dpS, dpY), Offset(dpX + dpS, dpY), strokeWidth=2f, cap=StrokeCap.Round)
                            
                            // 3. Buttons (Right) - 4 dots diamond
                            val bX = w * 0.7f
                            val bY = h * 0.45f
                            val bR = 1.5f // Radius
                            val bOff = w * 0.08f
                            drawCircle(c, bR, Offset(bX, bY - bOff)) // Top
                            drawCircle(c, bR, Offset(bX, bY + bOff)) // Bottom
                            drawCircle(c, bR, Offset(bX - bOff, bY)) // Left
                            drawCircle(c, bR, Offset(bX + bOff, bY)) // Right
                         }
                     }
                  }
            
            // Custom Buttons Loop
            configs.keys.toList().forEach { key ->
                if (key.startsWith("BTN_")) {
                     EditableComponent(key, isEditMode, selectedId == key, configs[key]!!, { selectedId = it }, { configs.remove(key); selectedId = null }) {
                         val conf = configs[key]!!
                          PSCenterButton("BTN", Modifier, componentBg, 0, false, ::vibrate) { _, pressed -> 
                             if (pressed) {
                                 if (conf.mappedKey > 0 && !activeCustomKeys.contains(conf.mappedKey)) activeCustomKeys.add(conf.mappedKey)
                             } else {
                                 if (conf.mappedKey > 0) activeCustomKeys.remove(conf.mappedKey)
                             }
                         }
                         // Overlay Label
                         if(conf.mappedKey > 0) {
                             Text(Char(conf.mappedKey).toString(), Modifier.align(Alignment.Center), color=Color.White, fontWeight=FontWeight.Bold)
                         }
                     }
                }
            }
            
            // Bottom Sizing Strip (Slider) for MainActivity Edit Mode
            if (isEditMode && selectedId != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 32.dp, end = 32.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .padding(horizontal = 24.dp)
                        .zIndex(100f),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SIZE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(16.dp))
                        val currentConf = configs[selectedId!!]
                        if (currentConf != null) {
                            Slider(
                                value = currentConf.scale,
                                onValueChange = { currentConf.scale = it },
                                valueRange = 0.5f..3.0f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = AppColors.Primary,
                                    activeTrackColor = AppColors.Primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                "${String.format("%.1f", currentConf.scale)}x",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
        } else if (currentMode == 1) {
            // ROBUST NEXUS TRACKPAD MODE
            val focusRequester = remember { FocusRequester() }
            var textState by remember { mutableStateOf(TextFieldValue(" ")) }
            val keyboardController = LocalSoftwareKeyboardController.current

            Box(Modifier.fillMaxSize()) {
                // 1. Grid Background
                Canvas(Modifier.fillMaxSize()) {
                    val step = 24.dp.toPx()
                    for (x in 0..size.width.toInt() step step.toInt()) {
                        for (y in 0..size.height.toInt() step step.toInt()) {
                            drawCircle(
                                color = Color(0xFF282E39).copy(alpha = 0.5f),
                                radius = 0.8.dp.toPx(),
                                center = Offset(x.toFloat(), y.toFloat())
                            )
                        }
                    }
                }

                // 2. Large Gesture Surface (Moved up in Z-order)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            val density = this.density
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                var maxPointers = 1
                                var isDrag = false

                                do {
                                    val event = awaitPointerEvent()
                                    val activeChanges = event.changes.filter { it.pressed }
                                    val currentPointers = activeChanges.size
                                    
                                    // Filter noise: Only upgrade pointer count if fingers are far enough apart
                                    if (currentPointers > maxPointers) {
                                        if (currentPointers >= 2) {
                                            val p1 = activeChanges[0].position
                                            val p2 = activeChanges[1].position
                                            val distPx = (p1 - p2).getDistance()
                                            val distDp = distPx / density
                                            // 25dp is a safe threshold for distinct fingers
                                            if (distDp > 25f) {
                                                maxPointers = currentPointers
                                            }
                                        } else {
                                            maxPointers = currentPointers
                                        }
                                    }

                                    var dx = 0f; var dy = 0f
                                    var movingCount = 0
                                    event.changes.forEach { 
                                         if(it.pressed) {
                                              dx += it.positionChange().x
                                              dy += it.positionChange().y
                                              movingCount++
                                         }
                                    }
                                    
                                    if(movingCount > 0) { 
                                        dx /= movingCount
                                        dy /= movingCount 
                                    }
                                    
                                    if (dx != 0f || dy != 0f) {
                                         // Use a smaller threshold for initial drag detection but keep noise floor
                                         if (!isDrag && (dx*dx + dy*dy) > 1.5f) isDrag = true
                                         
                                         if (isDrag) {
                                              event.changes.forEach { if(it.positionChange() != Offset.Zero) it.consume() }
                                              if (maxPointers >= 2) {
                                                  // Scroll Mode - Scale slightly for better feel
                                                  onScroll(dx * 0.8f, dy * 0.8f)
                                              } else {
                                                  // Mouse Move Mode
                                                  onMouseMove(dx, dy, false, false)
                                              }
                                         }
                                    }
                                } while (event.changes.any { it.pressed })
                                
                                // Ensure all buttons released at end of gesture
                                onMouseMove(0f, 0f, false, false)

                                if (!isDrag) {
                                     // Tap detection
                                     if (maxPointers == 1) {
                                          scope.launch { 
                                              onMouseMove(0f, 0f,true,false)
                                              delay(35)
                                              onMouseMove(0f, 0f,false,false)
                                          }
                                     } else if (maxPointers >= 2) {
                                          scope.launch { 
                                              onMouseMove(0f, 0f,false,true)
                                              delay(35)
                                              onMouseMove(0f, 0f,false,false)
                                          }
                                     }
                                }
                            }
                        }
                )

                // 3. Floating Interactive Buttons (Top Layer)
                // Sidebar Button
                IconButton(
                    onClick = { onToggleMenu() },
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                        .background(Color.Black, CircleShape)
                ) {
                    Icon(Icons.Rounded.Menu, null, tint = Color.Gray)
                }

                // Floating Keyboard Button
                val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .imePadding()
                        .padding(24.dp)
                        .size(56.dp)
                        .shadow(12.dp, CircleShape, spotColor = if (isImeVisible) Color.Red else Color(0xFF0D59F2))
                        .background(if (isImeVisible) Color.Red else Color(0xFF0D59F2), CircleShape)
                        .clickable { 
                            if (isImeVisible) {
                                keyboardController?.hide()
                            } else {
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (isImeVisible) Icons.Rounded.Close else Icons.Rounded.Keyboard, null, tint = Color.White)
                }

                // Hidden TextField for Input (Robust Space-Buffered)
                TextField(
                    value = textState,
                    onValueChange = { newValue ->
                        val text = newValue.text
                        if (text.isEmpty()) {
                            // Backspace case (space deleted)
                            onSendText("\b")
                            textState = TextFieldValue(" ", selection = TextRange(1))
                        } else if (text == " ") {
                            // No change in character count, maybe just cursor moved
                            textState = newValue.copy(selection = TextRange(1))
                        } else {
                            // Character added
                            // Extract only non-space characters
                            val existingChar = text.filter { it != ' ' }
                            if (existingChar.isNotEmpty()) {
                                onSendText(existingChar)
                            }
                            textState = TextFieldValue(" ", selection = TextRange(1))
                        }
                    },
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        onSendText("\n")
                    })
                )
            }
        } else if (currentMode == 2) {
            // PREMIUM STITCH RACING WHEEL
            var rotation by remember { mutableFloatStateOf(0f) }
            var speed by remember { mutableIntStateOf(0) }
            var gear by remember { mutableIntStateOf(1) }
            
            
            // Background with carbon texture and neon glows
            Box(Modifier.fillMaxSize().background(AppColors.CarbonDark)) {
                // Carbon Pattern Canvas
                Canvas(Modifier.fillMaxSize()) {
                    val stepSize = 20.dp.toPx()
                    for (x in 0..size.width.toInt() step stepSize.toInt()) {
                        for (y in 0..size.height.toInt() step stepSize.toInt()) {
                            drawRect(
                                color = Color.White.copy(alpha = 0.02f),
                                topLeft = Offset(x.toFloat(), y.toFloat()),
                                size = Size(stepSize/2, stepSize/2)
                            )
                        }
                    }
                    
                    // Neon ambient glows
                    drawCircle(AppColors.NeonBlue.copy(alpha=0.05f), radius = 400f, center = Offset(size.width * 0.2f, size.height * 0.5f))
                    drawCircle(AppColors.NeonRed.copy(alpha=0.05f), radius = 300f, center = Offset(size.width * 0.8f, size.height * 0.5f))
                }

                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Side: INTERACTIVE WHEEL
                    Box(Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
                        RacingWheel(rotation = rotation) { newRot ->
                            rotation = newRot
                            // Map -135..135 to 0..255 for Steering (LeftX)
                            leftX = (((newRot / 135f) + 1f) * 127.5f).toInt().coerceIn(0, 255)
                        }
                    }

                    // Center: TELEMETRY DASHBOARD
                    Box(Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
                        RacingDashboard(speed = speed, gear = gear)
                        
                        // Dynamic Speed simulation for demo
                        LaunchedEffect(rightTrigger, leftTrigger) {
                            while(true) {
                                if (rightTrigger > 0) {
                                    speed = (speed + (rightTrigger / 50)).coerceIn(0, 299)
                                } else if (speed > 0) {
                                    speed = (speed - 2).coerceAtLeast(0)
                                }
                                
                                if (leftTrigger > 0) {
                                    speed = (speed - (leftTrigger / 20)).coerceAtLeast(0)
                                }
                                
                                gear = when {
                                    speed < 30 -> 1
                                    speed < 70 -> 2
                                    speed < 120 -> 3
                                    speed < 180 -> 4
                                    speed < 240 -> 5
                                    else -> 6
                                }
                                delay(50)
                            }
                        }
                    }

                    // Right Side: TRIPLE PEDALS
                    Row(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Clutch (Yellow)
                        RacingPedal("CLUTCH", Modifier.fillMaxHeight(0.7f), color = AppColors.NeonYellow, width = 60.dp) { v ->
                            // Use buttons or other axis for clutch if needed
                        }
                        
                        // Brake (Red)
                        RacingPedal("BRAKE", Modifier.fillMaxHeight(0.85f), color = AppColors.NeonRed, width = 100.dp) { v ->
                            leftTrigger = (v * 255).toInt()
                            if (v > 0 && touchVibration) vibrate()
                        }
                        
                        // Gas (Blue)
                        RacingPedal("GAS", Modifier.fillMaxHeight(0.95f), color = AppColors.NeonBlue, width = 80.dp) { v ->
                            rightTrigger = (v * 255).toInt()
                        }
                    }
                }
            }
        }

        // Edit Toolbar Removed




        // Sidebar menu is now handled at the MainActivity level via StitchSidebar
    }
}



// Components moved to ControllerShared.kt
@Composable
fun ProfileManagerDialog(
    currentProfiles: List<String>,
    onDismiss: () -> Unit,
    onLoad: (String) -> Unit,
    onSaveNew: (String) -> Unit,
    onDelete: (String) -> Unit,
    onResetDefault: () -> Unit
) {
    var newProfileName by remember { mutableStateOf("") }
    
    // Simple Full Screen Overlay style Dialog manually since Dialog composable can be finicky without platform imports
    // Actually, Dialog() is available in androidx.compose.ui.window.Dialog
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Layout Profiles", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(Modifier.height(16.dp))
                
                // Save New
                Text("Save Current As:", color = Color.Gray, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = newProfileName, 
                        onValueChange = { newProfileName = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if(newProfileName.isNotBlank()) { onSaveNew(newProfileName); newProfileName="" } }) {
                        Text("SAVE")
                    }
                }
                
                Button(
                    onClick = onResetDefault,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) {
                    Text("RESET TO STANDARD LAYOUT", color = Color.White, fontWeight = FontWeight.Bold)
                }
                
                Spacer(Modifier.height(16.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
                
                // List
                Text("Saved Profiles:", color = Color.Gray, fontSize = 12.sp)
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    item {
                         // Always show Default
                         Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                         ) {
                             Text("Default (Autosave)", fontSize = 16.sp, color = Color.Black)
                             Button(onClick = { onLoad("autosave") }) { Text("LOAD") }
                         }
                         Divider()
                    }
                    items(currentProfiles.size) { idx ->
                         val name = currentProfiles[idx]
                         Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                         ) {
                            Text(
                                name, 
                                fontSize = 16.sp, 
                                color = Color.Black, 
                                modifier = Modifier.weight(1f).padding(end=8.dp)
                             )
                             Row {
                                 Button(onClick = { onLoad(name) }) { Text("LOAD") }
                                 Spacer(Modifier.width(4.dp))
                                 Button(
                                     onClick = { onDelete(name) },
                                     colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                 ) { Text("X") }
                             }
                         }
                         Divider()
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                    Text("Close")
                }
            }
        }
    }
}

 
@Composable
fun KeyboardDialog(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Remote Keyboard", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(Modifier.height(16.dp))
                
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Type to send to PC...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 4
                )
                
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if(text.isNotEmpty()) onSend(text) }) {
                        Text("SEND")
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureLabel(label: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        val accent = Color(0xFF0d59f2)
        Box(modifier = Modifier.size(6.dp).background(accent.copy(alpha = 0.8f), CircleShape))
        Text(
            text = label.uppercase(),
            color = Color.LightGray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun NexusModeToggle(
    currentSubMode: String, // "mouse" or "gyro"
    onModeChange: (String) -> Unit
) {
    val backgroundColor = Color(0xFF1F2633)

    Row(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeOption(
            label = "Trackpad",
            icon = Icons.Rounded.Mouse,
            isSelected = currentSubMode == "mouse",
            onClick = { onModeChange("mouse") }
        )
        ModeOption(
            label = "Gyro",
            icon = Icons.Rounded.Rotate90DegreesCcw,
            isSelected = currentSubMode == "gyro",
            onClick = { onModeChange("gyro") }
        )
    }
}

@Composable
private fun ModeOption(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val selectedColor = Color(0xFF2C3545)
    val accent = Color(0xFF0D59F2)
    val textColor = if (isSelected) accent else Color.Gray

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) selectedColor else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label.uppercase(),
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun RacingPedal(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = AppColors.NeonBlue,
    width: androidx.compose.ui.unit.Dp = 80.dp,
    onValueChange: (Float) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val pressure by animateFloatAsState(if (isPressed) 1f else 0f, label = "pressure", animationSpec = spring(stiffness = Spring.StiffnessLow))
    
    Column(
        modifier = modifier
            .width(width)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    isPressed = true
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    isPressed = false
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Pedal Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer { 
                    translationY = pressure * 20f
                    rotationX = -pressure * 10f
                    scaleX = 1f - (pressure * 0.05f)
                }
                .shadow(if(isPressed) 20.dp else 4.dp, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 12.dp, bottomEnd = 12.dp), spotColor = color, ambientColor = color)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF444444), Color(0xFF1A1A1A))
                    ),
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
                )
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 12.dp, bottomEnd = 12.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Brushed Metal Texture (Simple Lines)
            Canvas(Modifier.fillMaxSize()) {
                repeat(10) { i ->
                    drawLine(Color.White.copy(alpha=0.03f), Offset(0f, size.height * i / 10f), Offset(size.width, size.height * i / 10f), strokeWidth = 1f)
                }
            }
            
            // Studs detail
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) {
                    Box(Modifier.size(8.dp).background(
                        brush = Brush.linearGradient(listOf(Color.Black, Color(0xFF333333))),
                        shape = CircleShape
                    ).border(1.dp, Color.White.copy(alpha=0.1f), CircleShape))
                }
            }
        }
        
        Spacer(Modifier.height(12.dp))
        Text(
            label.uppercase(), 
            color = if(isPressed) color else Color.Gray, 
            fontSize = 10.sp, 
            fontWeight = FontWeight.Black, 
            letterSpacing = 2.sp
        )
        
        Spacer(Modifier.height(4.dp))
        // Progress bar at bottom
        Box(Modifier.fillMaxWidth(0.8f).height(2.dp).background(Color.White.copy(alpha=0.05f), CircleShape)) {
            Box(Modifier.fillMaxWidth(pressure).fillMaxHeight().background(color, CircleShape).shadow(4.dp, spotColor = color, ambientColor = color))
        }
    }
    
    LaunchedEffect(isPressed) {
        onValueChange(if(isPressed) 1f else 0f)
    }
}

@Composable
fun RacingDashboard(speed: Int, gear: Int) {
   Box(
       modifier = Modifier
           .width(220.dp)
           .height(280.dp)
           .animateContentSize()
           .background(Color(0xFF0A0A0A).copy(alpha=0.8f), RoundedCornerShape(32.dp))
           .border(1.dp, Color.White.copy(alpha=0.05f), RoundedCornerShape(32.dp))
           .padding(2.dp)
   ) {
       // Glassy top gradient
       Box(Modifier.fillMaxWidth().height(100.dp).background(
           Brush.verticalGradient(listOf(Color.White.copy(alpha=0.05f), Color.Transparent)),
           RoundedCornerShape(topStart=32.dp, topEnd=32.dp)
       ))

       Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
           // RPM LED Strip
           Row(
               Modifier.fillMaxWidth().height(30.dp), 
               horizontalArrangement = Arrangement.spacedBy(3.dp),
               verticalAlignment = Alignment.Bottom
           ) {
               repeat(10) { i ->
                   val color = when {
                       i < 6 -> Color(0xFF22C55E) // Green
                       i < 8 -> Color(0xFFFBBF24) // Yellow
                       else -> Color(0xFFEF4444) // Red
                   }
                   val isActive = speed > (i * 18)
                   
                   Box(
                       Modifier
                           .weight(1f)
                           .fillMaxHeight(0.3f + (i * 0.07f))
                           .clip(RoundedCornerShape(2.dp))
                           .background(color.copy(alpha = if (isActive) 1f else 0.1f))
                   ) {
                       if (isActive) {
                           Box(Modifier.fillMaxSize().shadow(10.dp, spotColor = color, ambientColor = color))
                       }
                   }
               }
           }
           
           Spacer(Modifier.weight(1f))
           
           // Gear Indicator
           Box(
               modifier = Modifier
                   .size(100.dp)
                   .background(Color(0xFF111111), RoundedCornerShape(16.dp))
                   .border(1.dp, Color.White.copy(alpha=0.05f), RoundedCornerShape(16.dp)),
               contentAlignment = Alignment.Center
           ) {
               Text(
                   text = gear.toString(), 
                   fontSize = 64.sp, 
                   fontWeight = FontWeight.Bold, 
                   color = Color.White,
                   fontFamily = FontFamily.Monospace
               )
               Text(
                   "GEAR", 
                   Modifier.align(Alignment.TopCenter).padding(top=8.dp), 
                   fontSize = 9.sp, 
                   color = Color.Gray, 
                   fontWeight = FontWeight.Bold, 
                   letterSpacing = 2.sp
               )
           }
           
           Spacer(Modifier.height(16.dp))
           
           // Speedometer
           Column(horizontalAlignment = Alignment.CenterHorizontally) {
               Text(
                   speed.toString(), 
                   fontSize = 56.sp, 
                   fontWeight = FontWeight.Black, 
                   color = Color.White,
                   letterSpacing = (-2).sp
               )
               Text(
                   "KM/H", 
                   fontSize = 10.sp, 
                   fontWeight = FontWeight.Black, 
                   color = AppColors.NeonBlue, 
                   letterSpacing = 2.sp
               )
           }
           
           Spacer(Modifier.height(12.dp))
           
           // Status Lights
           Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
               Text("ABS", fontSize=9.sp, fontWeight=FontWeight.Bold, color=Color.DarkGray)
               Text("TCS", fontSize=9.sp, fontWeight=FontWeight.Bold, color=AppColors.NeonBlue)
           }
       }
   }
}

@Composable
fun RacingWheel(rotation: Float, onRotate: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .size(320.dp),
        contentAlignment = Alignment.Center
    ) {
        // Draggable Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        val newRot = (rotation + dragAmount.x * 0.4f).coerceIn(-135f, 135f)
                        onRotate(newRot)
                        change.consume()
                    }
                }
        )

        // Visual Wheel
        Box(
            modifier = Modifier
                .size(320.dp)
                .rotate(rotation),
            contentAlignment = Alignment.Center
        ) {
            // Main Rim
            Canvas(Modifier.fillMaxSize()) {
                // Background Shadow/Depth
                drawCircle(Color.Black, radius = size.width / 2, style = Stroke(width = 24.dp.toPx()))
                // Main Texture
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFF2A2A2A), Color.Black)),
                    radius = size.width / 2, 
                    style = Stroke(width = 20.dp.toPx())
                )
                // Inner highlight
                drawCircle(Color.White.copy(alpha=0.05f), radius = (size.width / 2) - 0.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
            }
            
            // Spokes & Center Piece
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF111111))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Cross Spokes
                Box(Modifier.fillMaxWidth().height(12.dp).background(Color(0xFF222222)))
                Box(Modifier.fillMaxHeight().width(12.dp).background(Color(0xFF222222)))
                
                // Hub
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .background(
                            brush = Brush.linearGradient(listOf(Color(0xFF333333), Color.Black)),
                            shape = CircleShape
                        )
                        .border(1.dp, Color.White.copy(alpha=0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.SportsEsports, 
                        null, 
                        tint = AppColors.NeonBlue.copy(alpha=0.8f), 
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            // Top Center Marker
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 4.dp)
                    .size(24.dp, 12.dp)
                    .background(AppColors.NeonBlue, RoundedCornerShape(4.dp))
                    .shadow(15.dp, spotColor = AppColors.NeonBlue, ambientColor = AppColors.NeonBlue)
            )
        }
    }
}
