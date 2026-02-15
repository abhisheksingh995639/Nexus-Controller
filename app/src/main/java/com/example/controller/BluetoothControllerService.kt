package com.example.controller

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.util.concurrent.Executor

class BluetoothControllerService : Service() {

    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothControllerService = this@BluetoothControllerService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        initializeBluetooth()
    }

    // Logging callback
    var onLog: ((String) -> Unit)? = null

    private fun log(msg: String) {
        Log.d("BTService", msg)
        onLog?.invoke(msg)
    }

    @SuppressLint("MissingPermission")
    private fun initializeBluetooth() {
        log("Initializing Bluetooth...")
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        
        if (bluetoothAdapter == null) {
            log("Error: Bluetooth Adapter is NULL")
            onStateChanged?.invoke(ConnectionState.ERROR_REGISTER_FAILED)
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            log("Error: Bluetooth is DISABLED")
            onStateChanged?.invoke(ConnectionState.ERROR_REGISTER_FAILED) 
            return
        }
        
        log("Requesting HID Profile Proxy...")
        val result = bluetoothAdapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    log("HID Profile Proxy Connection ESTABLISHED")
                    bluetoothHidDevice = proxy as BluetoothHidDevice
                    registerApp()
                } else {
                    log("Connected to unknown profile: $profile")
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    log("HID Profile Proxy Connection LOST")
                    bluetoothHidDevice = null
                    onStateChanged?.invoke(ConnectionState.INITIALIZING) // Reset
                }
            }
        }, BluetoothProfile.HID_DEVICE)
        
        if (!result) {
            log("Error: getProfileProxy returned FALSE. This device likely does NOT support Bluetooth HID Device Role.")
            onStateChanged?.invoke(ConnectionState.ERROR_REGISTER_FAILED)
        } else {
            log("getProfileProxy returned TRUE. Waiting for callback...")
        }
    }

    // Detailed State for UI
    enum class ConnectionState {
        INITIALIZING,
        READY_TO_PAIR,
        CONNECTED,
        ERROR_REGISTER_FAILED // New state to catch failures
    }

    var onStateChanged: ((ConnectionState) -> Unit)? = null

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Controller App",
            "Android Gamepad",
            "Android Factory",
            0x00,
            getReportDescriptor()
        )

        val qosSettings = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_GUARANTEED,
            800,
            9,
            0,
            11250,
            BluetoothHidDeviceAppQosSettings.MAX
        )

        bluetoothHidDevice?.registerApp(
            sdpSettings,
            null,
            qosSettings,
            Executor { it.run() },
            object : BluetoothHidDevice.Callback() {
                override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                    Log.d("BTService", "App registered: $registered")
                    if (registered) {
                        onStateChanged?.invoke(ConnectionState.READY_TO_PAIR)
                    } else {
                        onStateChanged?.invoke(ConnectionState.ERROR_REGISTER_FAILED)
                    }
                }

                override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                    Log.d("BTService", "Connection state: $state")
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        hostDevice = device
                        onStateChanged?.invoke(ConnectionState.CONNECTED)
                    } else {
                        hostDevice = null
                        // If disconnected, valid state matches "Ready to Pair" (Registered but no host)
                        onStateChanged?.invoke(ConnectionState.READY_TO_PAIR)
                    }
                }
            }
        )
    }

    private fun getReportDescriptor(): ByteArray {
        return byteArrayOf(
            0x05, 0x01,       // Usage Page (Generic Desktop)
            0x09, 0x05,       // Usage (Gamepad)
            0xA1.toByte(), 0x01, // Collection (Application)
            0x05, 0x01,       //   Usage Page (Generic Desktop)
            0x09, 0x30,       //   Usage (X)
            0x09, 0x31,       //   Usage (Y)
            0x09, 0x32,       //   Usage (Z) - Right X
            0x09, 0x35,       //   Usage (Rz) - Right Y
            0x15, 0x81.toByte(), //   Logical Minimum (-127)
            0x25, 0x7F,       //   Logical Maximum (127)
            0x75, 0x08,       //   Report Size (8)
            0x95.toByte(), 0x04,       //   Report Count (4)
            0x81.toByte(), 0x02, //   Input (Data, Var, Abs)
            
            0x05, 0x09,       //   Usage Page (Button)
            0x19, 0x01,       //   Usage Minimum (Button 1)
            0x29, 0x10,       //   Usage Maximum (Button 16)
            0x15, 0x00,       //   Logical Minimum (0)
            0x25, 0x01,       //   Logical Maximum (1)
            0x75, 0x01,       //   Report Size (1)
            0x95.toByte(), 0x10,       //   Report Count (16)
            0x81.toByte(), 0x02, //   Input (Data, Var, Abs)
            
            0xC0.toByte()     // End Collection
        )
    }

    @SuppressLint("MissingPermission")
    fun sendReport(buttons: Int, leftX: Int, leftY: Int, rightX: Int, rightY: Int) {
        if (hostDevice != null) {
            val report = ByteArray(6)
            report[0] = leftX.toByte()
            report[1] = leftY.toByte()
            report[2] = rightX.toByte()
            report[3] = rightY.toByte()
            report[4] = (buttons and 0xFF).toByte()
            report[5] = ((buttons shr 8) and 0xFF).toByte()
            
            bluetoothHidDevice?.sendReport(hostDevice, 0, report)
        }
    }
}
