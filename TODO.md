# ZeroTap Roadmap & Future Work

## Phase 13: Edge AI Integration
- [ ] Implement `OnDeviceMotionInferenceEngine` using TensorFlow Lite / ONNX Runtime.
- [ ] Train a lightweight 1D-CNN or LSTM on human activity recognition (HAR) datasets for fall and assault detection.
- [ ] Benchmark Snapdragon NPU execution using Qualcomm Neural Processing SDK (QNN).

## Phase 14: On-Device Audio Intelligence
- [ ] Integrate YAMNet or lightweight Edge-Audio classifier for acoustic distress recognition (screams, glass breaks, distress shouts).
- [ ] Implement circular keyword detection with zero cloud transmission.

## Phase 15: Local LLM Incident Summaries
- [ ] Integrate Google Gemini Nano via Android AICore / ExecuTorch.
- [ ] Provide structured sensor timelines to local LLM for concise dispatch briefs.

## Phase 16: Off-Grid Mesh Networking
- [ ] Implement `BluetoothRelayTransport` using BLE Advertising & Scanning.
- [ ] Implement `WifiDirectRelayTransport` for peer-to-peer neighborhood safety relays when cellular towers are down.

## Phase 17: UI & UX Polish
- [ ] Interactive incident countdown timer with cancel gesture.
- [ ] Live map tile integration with cached offline OpenStreetMap overlays.
