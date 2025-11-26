# Privacy Policy for HeartOSC

**Last Updated: November 26, 2025**

## Introduction

This Privacy Policy describes how HeartOSC ("the App", "we", "our") handles information when you use our Android application. We are committed to protecting your privacy and being transparent about our practices.

## Information Collection and Use

### Information We Collect

HeartOSC does **NOT** collect, store, or transmit any personal information to our servers or any third parties. The App operates entirely on your device and local network.

The App accesses the following data solely for functionality purposes:

1. **Heart Rate Data**: The App reads heart rate measurements from your Bluetooth Low Energy (BLE) heart rate monitor in real-time. This data is:
   - Read directly from your connected heart rate monitor
   - Processed locally on your device
   - Transmitted only to the local network address you configure
   - **Never stored** on your device
   - **Never sent** to our servers or any external services

2. **Network Configuration**: The App stores your OSC configuration settings (IP address, port, and parameter names) locally on your device for connecting to your local network applications.

### How We Use Information

Heart rate data is used exclusively to:
- Display your current heart rate in the App interface
- Transmit the data to the local network destination you specify via the OSC (Open Sound Control) protocol
- Generate heartbeat pulse signals for synchronization purposes

All processing occurs locally on your device. We do not have access to your heart rate data or any other information from the App.

## Data Transmission

### Local Network Communication

The App transmits heart rate data over your local network using the OSC protocol via UDP (User Datagram Protocol). This transmission:
- Is sent **only** to the IP address and port you manually configure
- Occurs over your local network (typically to another device you own)
- Is **unencrypted** (standard for OSC protocol)
- Contains only heart rate values and connection status
- Does not include any personally identifiable information

**Important Security Note**: Because OSC communication is unencrypted, any device on your local network could potentially intercept the heart rate data being transmitted. We recommend using the App only on trusted networks.

## Data Storage

The App stores the following information locally on your device:
- OSC configuration settings (host IP address, port, parameter paths)
- Pulse duration preference
- Language preference

This information is stored using Android's encrypted SharedPreferences and:
- Remains on your device only
- Is never transmitted to us or any third party
- Can be deleted by uninstalling the App

## Permissions

The App requires the following permissions to function:

### Required Permissions

1. **Bluetooth Permissions** (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`):
   - Purpose: To scan for and connect to your BLE heart rate monitor
   - Data accessed: BLE device names and heart rate measurements
   - Data retention: No data is stored; all readings are processed in real-time

2. **Location Permission** (`ACCESS_FINE_LOCATION`):
   - Purpose: Required by Android for BLE scanning (Android system requirement)
   - Data accessed: The App does **NOT** access or use your location data
   - Note: This is a technical requirement imposed by Android OS for BLE functionality

### Optional Permissions

3. **Notification Permission** (`POST_NOTIFICATIONS`):
   - Purpose: To display a persistent notification when monitoring heart rate in the background
   - Data accessed: None
   - Note: You can deny this permission; the App will still function but without background notifications

## Third-Party Services

HeartOSC does **NOT**:
- Use any analytics services
- Include any advertising networks
- Integrate with any third-party data collection services
- Connect to any remote servers or APIs

The App is designed to work entirely offline and locally.

## Children's Privacy

HeartOSC does not knowingly collect information from children under the age of 13. Since we do not collect any personal information from any users, the App may be used by individuals of any age. However, we recommend parental supervision for children using heart rate monitors.

## Data Security

While we do not collect or store your personal information on our servers, we take the security of data on your device seriously:
- Configuration settings are stored using Android's encrypted SharedPreferences
- Heart rate data is processed in memory only and is not written to storage
- Network transmission uses standard OSC protocol over UDP

**User Responsibility**: Since OSC data is transmitted unencrypted over your local network, you are responsible for:
- Ensuring your local network is secure
- Using the App only on trusted networks
- Protecting your device with appropriate security measures

## Data Retention and Deletion

The App does not retain any heart rate data. Configuration settings stored on your device can be deleted by:
- Clearing the App's data in Android settings
- Uninstalling the App

## Your Rights

Since we do not collect or process your personal information, there is no data for us to access, modify, or delete. You maintain complete control over:
- The heart rate monitor you connect to
- The network destination for data transmission
- All configuration settings stored locally on your device

## Changes to This Privacy Policy

We may update this Privacy Policy from time to time. Any changes will be reflected by updating the "Last Updated" date at the top of this policy. We encourage you to review this policy periodically.

For major changes, we will notify users through:
- An updated version of this document in the App repository
- Release notes in the Google Play Store listing

## Open Source

HeartOSC is open source software. You can review our complete source code to verify our privacy practices at:
https://github.com/hizkifw/HeartOSC

## Contact Information

If you have questions or concerns about this Privacy Policy, please:
- Open an issue on our GitHub repository: https://github.com/hizkifw/HeartOSC/issues
- Contact us at: [Your contact email]

## Legal Basis for Processing (GDPR)

For users in the European Economic Area (EEA):
- We do not collect or process personal data as defined by GDPR
- All data processing occurs locally on your device
- You maintain full control over your data at all times

## California Privacy Rights (CCPA)

For users in California:
- We do not sell personal information
- We do not collect personal information as defined by CCPA
- All data remains under your control on your device

## Consent

By using HeartOSC, you consent to this Privacy Policy and agree to its terms.

---

**Summary**: HeartOSC is a privacy-focused application that does not collect, store, or transmit any personal information to external servers. Your heart rate data is processed locally and sent only to the destination you configure on your local network. We have no access to your data.
