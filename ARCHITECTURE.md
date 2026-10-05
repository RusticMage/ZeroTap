# ZeroTap Architecture

The current end-to-end architecture diagram and runtime pipeline descriptions live in the [README](README.md#architecture-and-end-to-end-flows). That document distinguishes active service wiring from development implementations, placeholders, and components that are present but not connected to the live flow.

## Runtime entry points

- `MainActivity` hosts the Compose UI and navigation graph.
- `ProtectionForegroundService` owns sensor collection, one-second risk evaluation, vehicle-accident detection, and emergency response wiring while protection is active.
- `ZeroTapDatabase` and `UserPreferences` provide Room persistence and DataStore settings.

## Two risk paths

The service currently runs two related paths over shared sensor data:

1. `DevelopmentRiskPredictionEngine` -> `TemporalRiskTracker` -> `ResponseManager` drives timed personal-safety escalation and direct SMS/call attempts.
2. `DevelopmentRiskEngine` -> `IncidentManager` maintains the legacy incident model and summary flow. It freezes an in-memory evidence snapshot, but does not currently dispatch via its injected `AlertManager`.

Vehicle accident handling is a separate pipeline: `VehicleAccidentDetector` -> `VehicleAccidentStateMachine` -> user check/countdown -> `ResponseManager` on timeout.

## Implementation boundary

The active classifiers and score engines are development heuristics. Keystore encryption, configurable internet alert transport, Bluetooth/Wi-Fi Direct transport classes, and future model integrations exist in the repository but are not all connected to the active service flow. See the README's [current behavior and limitations](README.md#current-behavior-and-limitations) for details.
