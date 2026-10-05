# ZeroTap — Standalone Vehicle License Plate AI

Modular, standalone Computer Vision & OCR pipeline for Indian vehicle registration plate recognition.

## Architecture

```text
Image
  ↓
Roboflow Serverless Detector (indian-license-plate-detection-6tmbr-ixo3p/2)
  ↓
Crop detected plate from ORIGINAL image
  ↓
Image Preprocessing (4 tailored variants: CLAHE, Adaptive Threshold, Sharpen, Original)
  ↓
PaddleOCR (RapidOCR PP-OCRv4 ONNX engine)
  ↓
Indian Number-Plate Normalization & Regex Validation
  ↓
Structured VehiclePlateResult (Strictly Real Inference)
```

## Setup & Environment

1. Install dependencies:
```bash
pip install -r vehicle_plate_ai/requirements.txt
```

2. Set your Roboflow API key:
- **Windows PowerShell**:
```powershell
$env:ROBOFLOW_API_KEY="your_roboflow_key_here"
```
- **Linux/macOS**:
```bash
export ROBOFLOW_API_KEY="your_roboflow_key_here"
```

## Running Inference

### CLI Usage:
```bash
python detect_plate.py path/to/vehicle.jpg
```

### JSON Output:
```bash
python detect_plate.py path/to/vehicle.jpg --json
```

## Negative Tests (Strict Reality Policy)
- Non-car images (e.g. photos of a hand, people, rooms, random street scenes) will return:
  `plateDetected = False`, `detectorConfidence = 0.0`, `registrationNumber = null`.
- Low-confidence or garbage OCR results are never forced into fake plates; `registrationNumber` remains `null`.
