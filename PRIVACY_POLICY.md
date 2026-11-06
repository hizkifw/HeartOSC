# Privacy Policy for HeartOSC

**Last Updated: November 6, 2026**

## Introduction

This Privacy Policy describes how HeartOSC ("the App", "we", "our") handles information when you use our Android phone application and its optional Wear OS companion app (together, "the App"). We are committed to protecting your privacy and being transparent about our practices.

HeartOSC has a single purpose: streaming your live heart rate to VRChat or other OSC (Open Sound Control) software that you configure, so it can be displayed on your avatar.

## Information Collection and Use

### Information We Collect

HeartOSC does **NOT** collect, store, or transmit any personal information to our servers or any third parties. We do not operate any servers for the App. The App runs entirely on your devices and your local network.

The App accesses the following data solely to provide its functionality:

1. **Heart Rate Data**: The App reads your live heart rate (beats per minute) in real time from one of these sources, chosen by you:
   - **A Bluetooth Low Energy (BLE) heart rate monitor** connected to your phone.
   - **Your Wear OS smartwatch's built-in heart rate sensor**, via the HeartOSC Wear OS companion app. The companion reads the sensor using Wear OS Health Services (with the Android sensor framework as a fallback).

   Heart rate data is:
   - Read only while a streaming session that you started is active
   - Processed in memory only
   - Transmitted only to the network address you configure (see "Data Transmission")
   - **Never stored** on your phone or watch
   - **Never logged** in release builds
   - **Never sent** to our servers or any third party

2. **Network Configuration**: The App stores your OSC configuration settings (IP address, port, and parameter names) locally on your phone so it can connect to your chosen OSC software.

### Health Connect

HeartOSC does **NOT** read, write, or query any data stored in Health Connect, and it does not access any historical or stored health records. The `android.permission.health.*` permissions used by the Wear OS companion app (see "Permissions") are used only to read the watch's live heart rate sensor.

### How We Use Information

Heart rate data is used exclusively to:
- Display your current heart rate in the App on your phone and watch
- Transmit it to the OSC destination you specify, so it can be shown on your avatar
- Generate heartbeat pulse signals for avatar animation

Heart rate data is not used for advertising, analytics, profiling, or any other purpose, and it is never sold or shared with third parties.

## Data Transmission

### Watch to Phone (Wear OS companion only)

When you use the Wear OS companion, the watch sends each live heart rate reading to the HeartOSC app on your paired phone using the Wearable Data Layer API, which is provided by Google Play services on your devices. The message contains only the current heart rate value. Google Play services handles delivery between your paired devices. HeartOSC does not send this data anywhere else.

### Phone to OSC Destination

The phone app sends heart rate data over your network using the OSC protocol over UDP (User Datagram Protocol). This transmission:
- Is sent **only** to the IP address and port that you manually configure (typically VRChat on your own PC)
- Is **unencrypted** (standard for the OSC protocol)
- Contains only heart rate values, derived values (for example, normalized heart rate and heartbeat pulses), and connection status
- Does not include any personally identifiable information

**Important Security Note**: Because OSC communication is unencrypted, other devices on the same network could potentially intercept the heart rate data being sent. We recommend using the App only on trusted networks.

## Background Access to Heart Rate Data

The Wear OS companion app can keep reading the heart rate sensor while the watch screen is dimmed or off, or while another app or watch face is in front. This allows your avatar's heart rate to stay live during a VR session, when you cannot interact with the watch.

Background access only happens during a streaming session that **you start** by tapping "Start". The session:
- Runs as a foreground service with a persistent notification on the watch
- Stops when you tap "Stop"

The App never reads heart rate data in the background outside a session you started.

## Data Storage

The App stores the following information locally in its private app storage (Android SharedPreferences):
- OSC configuration settings (host IP address, port, parameter paths)
- Pulse duration preference
- Language preference
- Selected heart rate source

This information:
- Remains on your device only
- Is never transmitted to us or any third party
- Can be deleted by clearing the App's data or uninstalling the App

Heart rate data is never written to storage.

## Permissions

### Phone App

1. **Bluetooth** (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`; `BLUETOOTH` and `BLUETOOTH_ADMIN` on Android 11 and older):
   - Purpose: To scan for and connect to your BLE heart rate monitor
   - Data accessed: BLE device names and heart rate measurements
   - Retention: None; readings are processed in real time

2. **Location** (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`):
   - Purpose: Required by Android for BLE scanning on some Android versions
   - The App does **NOT** access, use, or store your location

3. **Network** (`INTERNET`):
   - Purpose: To send OSC messages to the destination you configure

4. **Foreground service** (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`):
   - Purpose: To keep streaming from your heart rate monitor or watch while the App is in the background, with a visible notification

5. **Notifications** (`POST_NOTIFICATIONS`, optional):
   - Purpose: To show the persistent notification while streaming

### Wear OS Companion App

1. **Heart rate sensor** (`android.permission.health.READ_HEART_RATE` on Wear OS 6 / Android 16 and newer; `BODY_SENSORS` on Wear OS 5 and older):
   - Purpose: To read your live heart rate from the watch's built-in sensor during a streaming session you started
   - Retention: None; readings are sent to your phone and discarded

2. **Background heart rate sensor access** (`android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND` on Wear OS 6 / Android 16 and newer; `BODY_SENSORS_BACKGROUND` on Wear OS 5 and older):
   - Purpose: To keep reading the live heart rate while the watch screen is dimmed or off during a session you started (see "Background Access to Heart Rate Data")

3. **Foreground service and sensors** (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_HEALTH`, `HIGH_SAMPLING_RATE_SENSORS`, `WAKE_LOCK`):
   - Purpose: To run the streaming session reliably, with a visible notification, until you stop it

4. **Notifications** (`POST_NOTIFICATIONS`):
   - Purpose: To show the persistent notification while streaming

## Third-Party Services

HeartOSC does **NOT**:
- Use any analytics or crash-reporting services
- Include any advertising networks
- Integrate with any third-party data collection services
- Connect to any developer-operated servers or APIs

The only third-party component involved in data transfer is Google Play services' Wearable Data Layer. It is used only to deliver heart rate readings from your watch to your paired phone when you use the Wear OS companion.

## Children's Privacy

HeartOSC does not knowingly collect information from children under the age of 13. Because we do not collect any personal information from any user, the App may be used by individuals of any age. However, we recommend parental supervision for children using heart rate monitors.

## Data Security

- Heart rate data is processed in memory only and is never written to storage
- Settings are kept in the App's private storage, which other apps cannot access
- OSC transmission uses the standard OSC protocol over UDP, which is unencrypted

**User Responsibility**: Because OSC data is sent unencrypted over your network, you are responsible for:
- Ensuring your network is secure
- Using the App only on trusted networks
- Protecting your devices with appropriate security measures

## Data Retention and Deletion

The App does not retain any heart rate data. You can delete the configuration settings stored on your device by:
- Clearing the App's data in Android settings
- Uninstalling the App

You can revoke any permission at any time in your phone's or watch's Settings.

## Your Rights

Because we do not collect or process your personal information, there is no data for us to access, modify, or delete. You keep complete control over:
- The heart rate source you use (BLE monitor or watch)
- When streaming starts and stops
- The network destination for data transmission
- All configuration settings stored on your device

## Changes to This Privacy Policy

We may update this Privacy Policy from time to time. Any changes will be reflected by updating the "Last Updated" date at the top of this policy. We encourage you to review this policy periodically.

For major changes, we will notify users through:
- An updated version of this document in the App repository
- Release notes in the Google Play Store listing

## Open Source

HeartOSC is open source software. You can review the complete source code to verify our privacy practices at:
https://github.com/hizkifw/HeartOSC

## Contact Information

If you have questions or concerns about this Privacy Policy, please:
- Open an issue on our GitHub repository: https://github.com/hizkifw/HeartOSC/issues
- Contact us at: [kitsune@kitsu.red](mailto:kitsune@kitsu.red)

## Legal Basis for Processing (GDPR)

For users in the European Economic Area (EEA):
- We do not collect or process personal data as defined by the GDPR
- All data processing occurs locally on your devices
- You keep full control over your data at all times

## California Privacy Rights (CCPA)

For users in California:
- We do not sell personal information
- We do not collect personal information as defined by the CCPA
- All data remains under your control on your devices

## Consent

By using HeartOSC, you consent to this Privacy Policy and agree to its terms.

---

**Summary**: HeartOSC does not collect, store, or transmit any personal information to external servers. Your live heart rate, from a BLE monitor or your Wear OS watch's sensor, is read only during a session you start. It is processed in memory and sent only to the OSC destination you configure. HeartOSC does not use Health Connect, and we have no access to your data.
