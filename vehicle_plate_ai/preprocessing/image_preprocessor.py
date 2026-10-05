import cv2
import numpy as np
from typing import List, Tuple

class ImagePreprocessor:
    """
    Produces carefully engineered visual preprocessing variants of the cropped license plate
    to maximize OCR character extraction precision across varying illumination, angles, and contrast.
    """

    @staticmethod
    def get_variants(image_bgr: np.ndarray) -> List[Tuple[str, np.ndarray]]:
        """
        Generates 4 distinct preprocessing variants from the plate crop.
        Returns a list of tuples: (variant_name, processed_image)
        """
        variants: List[Tuple[str, np.ndarray]] = []
        if image_bgr is None or image_bgr.size == 0:
            return variants

        h, w = image_bgr.shape[:2]

        # Target standard plate height for optimal character aspect ratio
        target_height = 100
        aspect = w / max(h, 1)
        target_width = int(target_height * aspect)
        target_width = max(target_width, 160)

        # Variant 1: Original resized with gentle bilateral smoothing
        resized = cv2.resize(image_bgr, (target_width, target_height), interpolation=cv2.INTER_CUBIC)
        variants.append(("variant_1_resized_original", resized))

        # Variant 2: Grayscale with CLAHE (Contrast Limited Adaptive Histogram Equalization)
        gray = cv2.cvtColor(resized, cv2.COLOR_BGR2GRAY)
        clahe = cv2.createCLAHE(clipLimit=2.5, tileGridSize=(8, 8))
        contrast_enhanced = clahe.apply(gray)
        variants.append(("variant_2_contrast_enhanced", contrast_enhanced))

        # Variant 3: Grayscale with Adaptive Gaussian Thresholding (high contrast binarization)
        denoised = cv2.GaussianBlur(contrast_enhanced, (3, 3), 0)
        adaptive_thresh = cv2.adaptiveThreshold(
            denoised, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 15, 4
        )
        variants.append(("variant_3_adaptive_threshold", adaptive_thresh))

        # Variant 4: Sharpened image (unsharp mask) to accentuate fine letter edges
        gaussian_blur = cv2.GaussianBlur(contrast_enhanced, (0, 0), 2.0)
        sharpened = cv2.addWeighted(contrast_enhanced, 1.8, gaussian_blur, -0.8, 0)
        variants.append(("variant_4_sharpened", sharpened))

        return variants
