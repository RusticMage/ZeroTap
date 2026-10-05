import os
import cv2
import numpy as np
from typing import Optional, List, Tuple
from rapidocr_onnxruntime import RapidOCR

from ..models.vehicle_plate_result import OcrResult
from ..preprocessing.image_preprocessor import ImagePreprocessor
from ..validation.indian_plate_validator import normalize_plate_text, validate_indian_plate

class PlateOcrEngine:
    """
    PaddleOCR-based engine using RapidOCR (official high-speed PaddleOCR ONNX models).
    Runs inference across engineered image variants and selects the best candidate
    based on actual OCR confidence and legitimate Indian license plate syntax validation.
    """

    def __init__(self):
        # Initializes RapidOCR with default PP-OCRv4 detection & recognition models
        self.engine = RapidOCR()

    def run_ocr_on_image(self, img_np: np.ndarray) -> Tuple[str, float]:
        """
        Executes OCR on a single image numpy array.
        Returns:
            (combined_raw_text, average_confidence)
        """
        if img_np is None or img_np.size == 0:
            return "", 0.0

        try:
            result, _ = self.engine(img_np)
            if not result:
                return "", 0.0

            texts: List[str] = []
            scores: List[float] = []

            for line in result:
                # Each line is: [bbox, text, score]
                if len(line) >= 3:
                    text_str = str(line[1]).strip()
                    try:
                        score_val = float(line[2])
                    except (ValueError, TypeError):
                        score_val = 0.0
                    
                    if text_str:
                        texts.append(text_str)
                        scores.append(score_val)

            if not texts:
                return "", 0.0

            combined_text = " ".join(texts)
            avg_score = float(np.mean(scores)) if scores else 0.0
            return combined_text, avg_score

        except Exception as e:
            return "", 0.0

    def extract_and_select_best(self, plate_crop_bgr: np.ndarray) -> Optional[OcrResult]:
        """
        Runs OCR on all preprocessed variants of the plate crop.
        Scores each candidate:
        - Valid Indian plate format gets substantial priority boost (+1.0)
        - OCR confidence differentiates candidates
        Returns the top-ranked OcrResult or None if no text was found.
        """
        variants = ImagePreprocessor.get_variants(plate_crop_bgr)
        if not variants:
            return None

        candidates: List[OcrResult] = []

        for variant_name, variant_img in variants:
            raw_text, confidence = self.run_ocr_on_image(variant_img)
            if not raw_text or confidence <= 0.0:
                continue

            normalized = normalize_plate_text(raw_text)
            is_valid, _ = validate_indian_plate(normalized)

            candidate = OcrResult(
                raw_text=raw_text,
                normalized_text=normalized,
                valid_format=is_valid,
                ocr_confidence=round(confidence, 4),
                variant_name=variant_name
            )
            candidates.append(candidate)

        if not candidates:
            return None

        # Sort candidates:
        # Priority 1: valid_format (True before False)
        # Priority 2: ocr_confidence (higher is better)
        candidates.sort(
            key=lambda c: (1 if c.valid_format else 0, c.ocr_confidence),
            reverse=True
        )

        return candidates[0]
