# ZeroTap – Zero-User-Action Incident Detection System

> **ZeroTap** is an on-device personal safety system designed around zero-user-action incident detection. It continuously interprets phone sensor context to detect abnormal or risk-escalating situations without requiring the user to unlock their phone or press an SOS button.

Developed for physical Android devices (demonstrated on iQOO Android devices).

---

## Architecture Overview

ZeroTap uses a strict unidirectional detection and decision pipeline:

```
+-------------------------------------------------------------+
|                      PHONE SENSORS                          |
|   Accelerometer  *  Gyroscope  *  Fused Location  *  Mic     |
+-------------------------------------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                     SENSOR MANAGERS                         |
|   MotionDataSource  *  LocationDataSource  *  AudioData     |
+-------------------------------------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                 NORMALIZED SENSOR EVENTS                    |
|   MotionSample  *  LocationSample  *  AudioMetadata         |
+-------------------------------------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                   AI / SIGNAL DETECTORS                     |
|   MotionInferenceEngine  *  AudioInferenceEngine            |
|   LocationContextAnalyzer                                   |
+-------------------------------------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                        RISK ENGINE                          |
|   DevelopmentRiskEngine (Deterministic Multi-Signal Scorer) |
+-------------------------------------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                   INCIDENT STATE MACHINE                    |
|   NORMAL -> WATCH -> SUSPICIOUS -> HIGH_RISK -> INCIDENT    |
+-------------------------------------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|                      INCIDENT MANAGER                       |
|   Coordinates evidence freeze, local summarizer & alerts    |
+-------------------------------------------------------------+
          |                    |                     |
          v                    v                     v
+------------------+ +--------------------+ +-----------------+
| ENCRYPTED BUFFER | | LOCAL SUMMARIZER   | | ALERT MANAGER   |
| Rolling 60s      | | Template /         | | Internet, SMS   |
| Keystore AES-GCM | | Future Local LLM   | | BT/WiFi Direct  |
+------------------+ +--------------------+ +-----------------+
```

### Safety Rule Enforced in Code
Individual sensor detectors **never** trigger alerts directly:
- Sensor Detectors -> Structured Signals (`AudioSignal`, `MotionSignal`, `LocationSignal`, `RouteSignal`)
- Signals -> `RiskEngine`
- `RiskAssessment` -> `IncidentManager`
- `IncidentManager` -> `ResponsePolicy` & `AlertManager`

---

## Implemented Features

### 1. Sensor Layer
- **MotionDataSource**: High-frequency accelerometer and gyroscope listener; computes 3D Euclidean magnitude and standard deviation across rolling motion windows.
- **LocationDataSource**: Fused Location Provider client emitting timestamped coordinates, speed, bearing, and accuracy.
- **AudioDataSource**: On-device PCM 16-bit 16kHz audio monitor calculating real-time amplitude in dB without recording or storing raw acoustic feeds.
- **LocationContextAnalyzer**: Detects unexpected stops, prolonged stationary events, and route deviations.

### 2. Risk Engine & State Machine
- **States**: `NORMAL`, `WATCH`, `SUSPICIOUS`, `HIGH_RISK`, `INCIDENT`, `RESOLVED`.
- **Deterministic Multi-Signal Scoring**:
  - Distress Audio: `+0.35`
  - Abrupt Motion (drop / sudden jerk): `+0.20` to `+0.25`
  - Unexpected Stop: `+0.15`
  - Prolonged Stop: `+0.20`
  - Route Deviation: `+0.15`
- Implements state **hysteresis** to prevent rapid flapping across severity levels.
- Fully labeled as a development scoring engine ready for trained ML models.

### 3. Rolling Evidence Black Box
- Circular in-memory buffer (`RollingEvidenceBuffer`) storing up to 60 seconds of pre-incident sensor history (motion samples, location fixes, and audio metadata).
- **Incident Freeze**: When risk elevates to `INCIDENT`, the pre-incident window is locked and combined with post-incident data into an `EvidenceSnapshot`.
- **Encryption**: Backed by **Android Keystore** AES-GCM with hardware-isolated keys (`EvidenceEncryptionService`). No raw keys or plaintext records are saved.

### 4. Alert & Communication Abstraction
- `AlertTransport` interface with pluggable dispatch strategies:
  - **InternetAlertTransport**: Sends JSON payload to user-configured safety endpoints.
  - **SmsAlertTransport**: SMS fallback via Android `SmsManager` providing coordinates and Google Maps link to trusted contacts.
  - **BluetoothRelayTransport & WifiDirectRelayTransport**: Clean architecture placeholders for future off-grid mesh communications.
- **AlertManager**: Fallback dispatch policy ensuring message delivery even under spotty network conditions.

### 5. AI Engine Interfaces & Local Summarizer
- Clean interfaces for future on-device ML:
  - `MotionInferenceEngine` (`DevelopmentMotionInferenceEngine` & `FutureOnDeviceMotionInferenceEngine` stub)
  - `AudioInferenceEngine` (`DevelopmentAudioInferenceEngine` & `FutureOnDeviceAudioInferenceEngine` stub)
  - `LocalIncidentSummarizer` (`DevelopmentIncidentSummarizer` & `FutureOnDeviceLlmSummarizer` stub)
- The development summarizer synthesizes structured timelines into human-readable situation reports without external network calls.

### 6. Persistence & Offline Operation
- **Room Database**: Tables for `incidents`, `risk_events`, `trusted_contacts`, `evidence_metadata`, and `alert_attempts`.
- **DataStore**: Preferences for sampling intervals, alert endpoints, and debug modes.

### 7. Background Protection Service
- `ProtectionForegroundService`: Persistent foreground service maintaining sensor collection, inference, and evidence buffering while surviving screen off and app switching.

### 8. User Interface (Jetpack Compose)
- **Home**: Protection ON/OFF toggle, live risk state badge, risk score meter, and active signal cards.
- **Protection Mode**: In-depth telemetry, buffer capacity meters, and live signal feed.
- **Active Incident**: Live emergency screen showing frozen evidence status, alert delivery log, and dismiss/resolve controls.
- **Incident History**: Room-backed list of past incidents with status and timestamps.
- **Trusted Contacts**: Add, view, and manage emergency phone numbers.
- **Settings**: Adjust sensor frequencies, set alert endpoint, and open the debug dashboard.
- **Developer Simulation Dashboard**: Injects real signals (`SUDDEN_JERK`, `DISTRESS_SOUND`, `UNEXPECTED_STOP`, `COMBINED_INCIDENT`) directly into the actual `RiskEngine` pipeline for testing.

---

## Package Structure

```
com.zerotap
├── ZeroTapApp.kt                     # Application entry point & ServiceLocator
├── MainActivity.kt                  # Compose root Activity
├── ai/
│   ├── audio/                       # Audio inference interfaces & dev engines
│   ├── llm/                         # Local summarizer interfaces & dev template
│   └── motion/                      # Motion classification interfaces & dev engines
├── alert/
│   ├── AlertManager.kt              # Transport dispatcher & retry fallback
│   ├── AlertTransport.kt            # Transport interface
│   ├── InternetAlertTransport.kt     # HTTP transport
│   ├── SmsAlertTransport.kt          # SMS transport
│   └── placeholder/                 # Mesh / Wi-Fi Direct placeholders
├── data/
│   ├── datastore/                   # UserPreferences DataStore
│   ├── db/                          # Room Database, Entities, and DAOs
│   └── repository/                  # Repositories for incidents, contacts, alerts
├── domain/
│   ├── incident/                    # IncidentManager & IncidentStateMachine
│   ├── model/                       # Domain models, Enums, and RiskSignals
│   └── risk/                        # RiskEngine interface & DevelopmentRiskEngine
├── evidence/
│   ├── EvidenceEncryptionService.kt # Keystore AES-GCM encryption
│   └── RollingEvidenceBuffer.kt     # Circular pre-incident buffer
├── security/
│   └── KeystoreManager.kt           # Hardware-backed Android Keystore wrapper
├── sensor/
│   ├── SensorDataSource.kt          # Generic sensor data source interface
│   ├── audio/                       # Microphone amplitude reader
│   ├── location/                    # Fused Location reader & Context Analyzer
│   └── motion/                      # Accelerometer & Gyroscope reader
├── service/
│   └── ProtectionForegroundService.kt# Persistent foreground background monitor
├── ui/
│   ├── components/                  # Shared Compose components
│   ├── contacts/                    # Trusted Contacts screen & VM
│   ├── debug/                       # Developer simulation dashboard & VM
│   ├── history/                     # Incident history screen & VM
│   ├── home/                        # Home screen & VM
│   ├── incident/                    # Active Incident screen & VM
│   ├── navigation/                  # Navigation graph & routes
│   ├── protection/                  # Live Protection screen & VM
│   ├── settings/                    # Settings screen & VM
│   └── theme/                       # Color, Type, Theme definitions
└── util/
    ├── Logger.kt                    # Categorized structured logger
    └── RollingBuffer.kt             # Generic thread-safe circular buffer
```

---

## Physical Device Testing Instructions (iQOO / Android)

### Prerequisites
1. Physical iQOO Android phone.
2. Enable **Developer Options** (Settings -> About Phone -> Tap *Build Number* 7 times).
3. In Developer Options, enable **USB Debugging**.
4. Connect the phone to your PC via USB cable.

### Deploying the Application
Run the following PowerShell command to verify device connectivity and install:

```powershell
# 1. Verify ADB sees the phone
& "C:\Users\Arfat\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices

# 2. Install the compiled debug APK directly
& "C:\Users\Arfat\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "d:\Hackathons\ZeroTap\app\build\outputs\apk\debug\app-debug.apk"

# 3. Launch the application on the phone
& "C:\Users\Arfat\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell am start -n com.zerotap/.MainActivity
```

### Verification Flow on Device
1. **Launch App**: The Home screen shows `ZERO TAP` in cyan with `Protection: OFF` and risk state `Low`.
2. **Configure Trusted Contact**: Go to the **Contacts** tab, tap `Add Contact`, and enter a phone number to receive alerts.
3. **Activate Protection**: Toggle the switch to **ON**. Accept runtime permissions (Location, Microphone, Notifications, SMS). A persistent foreground service notification will appear in the system tray.
4. **Physical Sensor Check**: Move the phone around or tap the table. Switch to the **Protection** tab to see live motion, audio, and location updates.
5. **Simulated Trigger**:
   - Navigate to **Settings** -> **Developer Dashboard**.
   - Tap **"Distress Audio"** or **"Sudden Motion"** -> notice the Risk Level immediately transition to `Watch` or `Suspicious`.
   - Tap **"Combined Incident"** -> risk score spikes above `0.70`, transitioning to `INCIDENT`.
6. **Active Incident Verification**:
   - The UI displays the red incident screen.
   - The pre-incident evidence buffer freezes and encrypts via Android Keystore.
   - An SMS alert attempt is logged and dispatched to your configured contact.
   - Tap **"Resolve"** or **"Dismiss"** to return to normal state.
7. **History Verification**: Open the **History** tab to inspect the saved incident report with its generated situation summary.

---

## Security Considerations
- **Keystore-Backed AES-GCM**: Encryption keys are generated inside the Android hardware-backed keystore. The master key never leaves secure hardware.
- **Zero Cloud Audio Streaming**: Audio is analyzed locally using amplitude calculations; raw acoustic audio is never streamed to any remote server.
- **Privacy Conscious Logging**: Structured logging (`[Sensor]`, `[AI]`, `[Risk]`, `[Incident]`, `[Alert]`) redacts precise raw coordinates and sensitive payloads from Logcat.
