# Nexus Controller

Nexus Controller transforms your Android device into a high-performance, low-latency gamepad for your Windows PC. Whether you need an Xbox 360 controller for modern titles or a DualShock 4 for compatibility, Nexus Controller delivers a seamless experience over Wi-Fi or USB.


## 🚀 Features

-   **Dual Emulation**: natively emulates **Xbox 360** and **DualShock 4** controllers using the industry-standard ViGEmBus driver.
-   **Ultra-Low Latency**: Built on a custom UDP protocol for real-time responsiveness.
-   **Haptic Feedback**: Feel the action with full rumble/vibration support redirected to your phone.
-   **4-Player Multiplayer**: Connect up to 4 devices simultaneously for local co-op gaming.
-   **Cyberpunk UI**: A premium, "Nexus Core" server interface with real-time telemetry graphs, dark mode, and 4 neon color themes (Cyan, Green, Orange, Red).
-   **Input Mapping**: Fully remappable buttons and axes via the server dashboard.
-   **Mouse & Keyboard Mode**: Toggle into a trackpad mode to control your PC mouse and keyboard from your phone.
-   **Easy Pairing**: Connect instantly via QR code scanning or auto-discovery.

## 🛠️ Prerequisites

### Windows PC (Server)
-   **OS**: Windows 10 or 11.
-   **Python**: Python 3.8+.
-   **Driver**: [ViGEmBus](https://github.com/nefarius/ViGEmBus/releases/latest) (Required for controller emulation).

### Android (Client)
-   **OS**: Android 9.0 (Pie) or higher.
-   **Network**: Wi-Fi (5GHz recommended) or USB Cable.

## 📦 Installation

### 1. PC Server Setup

1.  **Install the Driver**:
    *   Run `ViGEmBusSetup.exe` located in this folder.
    *   *Note: This is critical. Converting phone input to Xbox input is impossible without this driver.*

2.  **Install Python Dependencies**:
    Open a terminal in this directory and run:
    ```bash
    pip install -r deps.txt
    ```
    *Core dependencies: `vgamepad`, `pywebview`, `qrcode`, `pynput`, `flask`.*

### 2. Android App Setup

The Android source code is located in the `/app` directory.

1.  Open the `/app` folder in **Android Studio**.
2.  Sync Gradle and build the project.
3.  Deploy to your device via USB debugging or build a signed APK.

## 🎮 Usage

### Starting the Server

The easiest way to launch the server is via the batch script, which automatically requests the necessary Administrator privileges (needed for keyboard/mouse emulation):

1.  Double-click **`RunServer.bat`**.
2.  The **Nexus Core** dashboard will launch.
3.  Click **INITIALIZE** to start the server.

### Connecting Your Phone

1.  **Wi-Fi Mode**:
    *   Ensure your Phone and PC are on the same Wi-Fi network.
    *   Open the Android App.
    *   Tap **Scan QR** and point it at the QR code on the PC dashboard.
    *   *Alternatively, wait for "Auto-Detect" or manually enter the IP displayed.*

2.  **USB Mode (Wired, Lowest Latency)**:
    *   Connect your phone via USB.
    *   Enable **USB Debugging** on your phone.
    *   The server will automatically attempt to set up `adb reverse` to forward traffic.
    *   Connect to `127.0.0.1` or `localhost` in the mobile app manually if auto-discovery doesn't pick it up.

### Dashboard Controls

-   **Visualizer**: See real-time stick and button inputs from connected devices.
-   **Config**: Click the "Config" button next to a connected unit to remap keys or toggle haptics.
-   **Themes**: Switch between Cyan, Green, Orange, or Red themes using the color dots in the header.

## 🔧 Troubleshooting

| Issue | Solution |
| :--- | :--- |
| **"Missing Driver" Error** | Install `ViGEmBusSetup.exe` and restart your PC. |
| **Phone won't connect** | 1. Check Firewall rules (Allow Python/UDP port 6000).<br>2. Ensure devices are on the same Wi-Fi.<br>3. Disable "AP Isolation" on your router. |
| **Input Lag** | Switch to a 5GHz Wi-Fi network or use USB tethering mode. |
| **Mouse mode not working** | Run the server as **Administrator** (use `RunServer.bat`). |

## 📜 License

This project is open-source.

