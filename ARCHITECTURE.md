# ZeroTap System Architecture

## Core Principles

1. **Zero-User-Action Incident Detection**: Continuous interpretation of phone sensors without requiring manual unlocks or SOS taps.
2. **On-Device First**: No cloud dependency for threat evaluation, incident state transitions, or evidence protection.
3. **Decoupled Pipeline**: Sensor detectors never directly trigger emergency responses.
4. **Hardware-Isolated Privacy**: Black box rolling evidence encrypted via Android Keystore AES-GCM.

---

## Detailed Component Architecture

### 1. Sensor Pipeline (`com.zerotap.sensor`)
- `SensorDataSource<T>` defines an asynchronous `dataFlow: Flow<T>` with explicit lifecycle control (`start()`, `stop()`, `isActive`).
- `MotionDataSource`:
  - Hooks `Sensor.TYPE_ACCELEROMETER` and `Sensor.TYPE_GYROSCOPE`.
  - Correlates timestamp samples into `MotionSample`.
  - Emits 3D acceleration $(a_x, a_y, a_z)$ and angular velocity $(g_x, g_y, g_z)$.
  - Calculates Euclidean norm: $\|a\| = \sqrt{a_x^2 + a_y^2 + a_z^2}$.
- `LocationDataSource`:
  - Hooks `FusedLocationProviderClient` with balanced power accuracy.
  - Normalizes updates into `LocationSample` $(lat, lon, accuracy, speed, bearing)$.
- `AudioDataSource`:
  - Opens `AudioRecord` (16kHz mono PCM 16-bit).
  - Computes root-mean-square amplitude in decibels: $dB = 20 \log_{10}(RMS)$.
  - Discards raw PCM buffers after calculating metadata.

### 2. Inference & Signal Detectors (`com.zerotap.ai`)
- Clean separation between interfaces and implementations:
  - `MotionInferenceEngine` -> `DevelopmentMotionInferenceEngine` (heuristics on variance, peak impact, and drop velocity) -> `FutureOnDeviceMotionInferenceEngine` (TFLite/ONNX/Qualcomm QNN placeholder).
  - `AudioInferenceEngine` -> `DevelopmentAudioInferenceEngine` (energy thresholds and sudden spike ratios) -> `FutureOnDeviceAudioInferenceEngine` (Yamnet / Edge sound classification placeholder).
  - `LocalIncidentSummarizer` -> `DevelopmentIncidentSummarizer` (synthesizes structured text) -> `FutureOnDeviceLlmSummarizer` (future Gemini Nano / ExecuTorch local LLM placeholder).

### 3. Risk Engine (`com.zerotap.domain.risk`)
- `RiskEngine` accepts a stream of typed `RiskSignal` instances:
  - `AudioSignal(classification, confidence)`
  - `MotionSignal(classification, confidence)`
  - `LocationSignal(type, speed, coords)`
  - `RouteSignal(deviationMeters)`
- Produces an immutable `RiskAssessment`:
  - `score: Float` (normalized $0.0 \dots 1.0$)
  - `state: RiskState` (`NORMAL`, `WATCH`, `SUSPICIOUS`, `HIGH_RISK`, `INCIDENT`, `RESOLVED`)
  - `contributingSignals: List<RiskSignal>`
- Incorporates hysteresis logic so brief sensor glitches do not immediately drop states from `HIGH_RISK` directly to `NORMAL`.

### 4. Incident Lifecycle (`com.zerotap.domain.incident`)
- `IncidentStateMachine`:
  - $DETECTED \to ACTIVE \to ALERTING \to RESOLVED$
  - Any state $\to DISMISSED$
- `IncidentManager`:
  - Receives new assessments.
  - Elevates status to `DETECTED` and `ACTIVE` on threshold breaches ($score \ge 0.50$).
  - Elevates to `INCIDENT` / `ALERTING` on sustained danger ($score \ge 0.70$).
  - Triggers the evidence buffer freeze.
  - Invokes `LocalIncidentSummarizer`.
  - Dispatches payloads via `AlertManager`.

### 5. Rolling Evidence Black Box (`com.zerotap.evidence`)
- `RollingEvidenceBuffer`:
  - Thread-safe ring buffer (`RollingBuffer<T>`) backed by `ArrayDeque`.
  - Capacity: 600 motion samples (~60s @ 10Hz), 12 location samples (~60s @ 5s), 120 audio metadata points.
  - `freeze()` takes an atomic snapshot of pre-incident samples and creates an immutable `EvidenceSnapshot`.
- `EvidenceEncryptionService`:
  - Uses `KeystoreManager` to access an AES key inside `AndroidKeyStore`.
  - Generates 128-bit initialization vectors (IV) via `SecureRandom`.
  - Encrypts the snapshot data using AES/GCM/NoPadding.
  - Stores the ciphertext file in app-internal sandboxed storage.

### 6. Communication & Alerts (`com.zerotap.alert`)
- `AlertTransport` strategy pattern:
  - Priority 1: `InternetAlertTransport` (Direct HTTP POST to webhook/API).
  - Priority 2: `SmsAlertTransport` (Direct SMS fallback with coordinates).
  - Priority 3 & 4: `BluetoothRelayTransport`, `WifiDirectRelayTransport` (Off-grid device-to-device relay stubs).
- `AlertManager`:
  - Iterates transports sequentially until a successful delivery confirmation is received.
  - Persists `AlertAttempt` audit trail to the Room database.

### 7. Background Service (`com.zerotap.service`)
- `ProtectionForegroundService`:
  - Registered in `AndroidManifest.xml` with `foregroundServiceType="location|microphone"`.
  - Runs with a persistent notification (`PRIORITY_LOW`).
  - Coroutine scope with `SupervisorJob` to isolate crashes in sensor readers.
