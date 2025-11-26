# HeartOSC

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A modern Android application that streams heart rate data from Bluetooth Low Energy (BLE) heart rate monitors to VRChat via OSC (Open Sound Control) protocol.

## Features

- 📱 **Bluetooth LE Heart Rate Monitor Support** - Connects to any standard BLE heart rate monitor
- 🎮 **VRChat OSC Integration** - Sends real-time heart rate data to VRChat avatars
- 🔄 **Automatic Reconnection** - Intelligent reconnection with exponential backoff
- 🌍 **Multi-language Support** - English, Japanese, Korean, and Simplified Chinese
- 🎨 **Material Design 3** - Modern, beautiful user interface
- 🔔 **Foreground Service** - Continues monitoring in the background with notification
- ⚙️ **Fully Configurable** - Customize OSC parameters and pulse behavior

## Screenshots

<!-- Add screenshots here when available -->

## Requirements

- Android 8.0 (API 26) or higher
- Bluetooth Low Energy (BLE) support
- A compatible heart rate monitor (any BLE HR monitor using standard Heart Rate Service)
- VRChat with OSC enabled

## Installation

### Download APK
1. Go to the [Releases](https://github.com/yourusername/HeartOSC/releases) page
2. Download the latest APK file
3. Install on your Android device

### Build from Source
1. Clone the repository:
   ```bash
   git clone https://github.com/hizkifw/HeartOSC.git
   cd HeartOSC
   ```

2. Open the project in Android Studio

3. Build and run:
   ```bash
   ./gradlew assembleDebug
   ```

## Usage

### First Time Setup

1. **Grant Permissions** - The app requires:
   - Bluetooth permissions (to scan and connect to heart rate monitors)
   - Location permission (required by Android for BLE scanning)
   - Notification permission (optional, for background service notification)

2. **Enable Bluetooth** - Make sure Bluetooth is turned on

3. **Configure OSC Settings**:
   - **OSC Host**: IP address of your VRChat instance
   - **OSC Port**: OSC port number (default: `9000`)
   - **Parameters**: Customize OSC parameter paths for your avatar

4. **Connect Heart Rate Monitor**:
   - Tap "Connect to Device"
   - Select your heart rate monitor from the list
   - Wait for connection to establish

### OSC Parameters

The app sends the following OSC parameters to VRChat:

| Parameter        | Type | Description                | Default Path                         |
|------------------|------|----------------------------|--------------------------------------|
| Heart Rate       | Int  | Current BPM value          | `/avatar/parameters/HR`              |
| HR Connected     | Bool | Monitor connection status  | `/avatar/parameters/isHRConnected`   |
| Heartbeat Toggle | Bool | Toggles with each beat     | `/avatar/parameters/HeartBeatToggle` |
| Heartbeat Pulse  | Bool | True during pulse duration | `/avatar/parameters/isHRBeat`        |

### Settings

Access settings to customize:
- **OSC Configuration**: Host, port, and parameter paths
- **Pulse Duration**: Duration of each heartbeat pulse (1-5000ms, default: 200ms)

## VRChat Avatar Setup

To use heart rate data in your VRChat avatar:

1. Enable OSC in VRChat
2. Add float parameters to your avatar matching the configured paths
3. Use the parameters in your avatar animations/expressions:
   - `HR` - Use for displaying BPM or controlling animations
   - `isHRConnected` - Show/hide heart rate UI elements
   - `HeartBeatToggle` - Trigger pulse animations
   - `isHRBeat` - Visual pulse effect during heartbeat

### Example Avatar Prefab

For a ready-to-use heart rate display implementation, check out [nullstalgia's Heart Rate Display prefab](https://nullstalgia.booth.pm/items/5156075) on BOOTH. This prefab provides a visual heart rate monitor that works with HeartOSC.

## Supported Heart Rate Monitors

Any Bluetooth LE heart rate monitor that implements the standard [Bluetooth Heart Rate Service](https://www.bluetooth.com/specifications/specs/heart-rate-service-1-0/) should work, including:

- Polar H10
- Wahoo TICKR
- Garmin HRM-Dual
- Coospo H6/H9
- And many others

## Troubleshooting

### Heart rate monitor not found
- Make sure your monitor is turned on and in pairing mode
- Check that Bluetooth is enabled on your device
- Ensure location permissions are granted
- Try restarting Bluetooth on your device

### VRChat not receiving data
- Verify the OSC host IP address is correct
- Check that VRChat OSC is enabled
- Ensure you're on the same network as your VRChat instance
- Try restarting VRChat

### Connection drops frequently
- Check battery level on your heart rate monitor
- Ensure your device stays within Bluetooth range
- Check for Bluetooth interference from other devices

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
