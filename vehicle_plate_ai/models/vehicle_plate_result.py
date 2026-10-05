from dataclasses import dataclass, asdict
from typing import Optional, Dict, Any

@dataclass
class BoundingBox:
    x: float
    y: float
    width: float
    height: float
    xmin: int
    ymin: int
    xmax: int
    ymax: int

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

@dataclass
class DetectionResult:
    detected: bool
    confidence: float
    bbox: Optional[BoundingBox]
    crop_path: Optional[str] = None
    class_name: Optional[str] = None

@dataclass
class OcrResult:
    raw_text: str
    normalized_text: str
    valid_format: bool
    ocr_confidence: float
    variant_name: str

@dataclass
class VehiclePlateResult:
    plateDetected: bool
    registrationNumber: Optional[str]
    detectorConfidence: float
    ocrConfidence: float
    rawOcrText: Optional[str]
    validFormat: bool
    boundingBox: Optional[Dict[str, Any]] = None
    cropPath: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)
