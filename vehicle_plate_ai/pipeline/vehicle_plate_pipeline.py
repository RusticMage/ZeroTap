import os
import cv2
from typing import Optional, List

from ..models.vehicle_plate_result import VehiclePlateResult
from ..detector.roboflow_detector import LicensePlateDetector
from ..ocr.paddle_ocr import PlateOcrEngine
from ..validation.indian_plate_validator import normalize_plate_text, validate_indian_plate

class VehiclePlatePipeline:
    """
    End-to-end standalone Computer Vision Pipeline:
    Input Vehicle Image
       ↓
    Roboflow License Plate Detector
       ↓
    Crop Plate Bounding Box from Original Image
       ↓
    Multi-variant Image Preprocessing
       ↓
    PaddleOCR Character Extraction
       ↓
    Indian Registration Validation & Normalization
       ↓
    Structured VehiclePlateResult (Strictly Real Inference)
    """

    def __init__(self, api_key: Optional[str] = None):
        self.detector = LicensePlateDetector(api_key=api_key)
        self.ocr_engine = PlateOcrEngine()

    def process(
        self,
        image_path: str,
        output_dir: str = "output",
        confidence_threshold: float = 0.35
    ) -> VehiclePlateResult:
        """
        Executes end-to-end detection and OCR on the given vehicle image.
        Never falls back or guesses; adheres strictly to actual inference values.
        """
        if not os.path.exists(image_path):
            raise FileNotFoundError(f"Image not found at path: {image_path}")

        # Run detector to locate license plate bounding boxes
        detections = self.detector.detect(
            image_path=image_path,
            output_crop_dir=output_dir,
            confidence_threshold=confidence_threshold
        )

        # Negative case: No plate detected
        if not detections:
            return VehiclePlateResult(
                plateDetected=False,
                registrationNumber=None,
                detectorConfidence=0.0,
                ocrConfidence=0.0,
                rawOcrText=None,
                validFormat=False,
                boundingBox=None,
                cropPath=None
            )

        # Select primary candidate (highest detector confidence)
        primary_det = detections[0]
        crop_path = primary_det.crop_path

        if not crop_path or not os.path.exists(crop_path):
            # If for any reason crop could not be saved to disk
            return VehiclePlateResult(
                plateDetected=True,
                registrationNumber=None,
                detectorConfidence=primary_det.confidence,
                ocrConfidence=0.0,
                rawOcrText=None,
                validFormat=False,
                boundingBox=primary_det.bbox.to_dict() if primary_det.bbox else None,
                cropPath=None
            )

        # Load plate crop and run PaddleOCR across preprocessing variants
        crop_bgr = cv2.imread(crop_path)
        ocr_result = self.ocr_engine.extract_and_select_best(crop_bgr)

        # If OCR produced no readable characters
        if not ocr_result or not ocr_result.normalized_text:
            return VehiclePlateResult(
                plateDetected=True,
                registrationNumber=None,
                detectorConfidence=primary_det.confidence,
                ocrConfidence=0.0,
                rawOcrText=None,
                validFormat=False,
                boundingBox=primary_det.bbox.to_dict() if primary_det.bbox else None,
                cropPath=crop_path
            )

        # Structured final response
        # If valid Indian format: provide registrationNumber
        # If OCR produced garbage: keep validFormat=False, registrationNumber=None (or normalized if caller wants raw inspect)
        final_reg_number = ocr_result.normalized_text if ocr_result.valid_format else None

        return VehiclePlateResult(
            plateDetected=True,
            registrationNumber=final_reg_number,
            detectorConfidence=primary_det.confidence,
            ocrConfidence=ocr_result.ocr_confidence,
            rawOcrText=ocr_result.raw_text,
            validFormat=ocr_result.valid_format,
            boundingBox=primary_det.bbox.to_dict() if primary_det.bbox else None,
            cropPath=crop_path
        )
