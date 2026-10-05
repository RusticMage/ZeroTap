import os
import cv2
import base64
import requests
from typing import List, Optional, Tuple, Dict, Any

from ..models.vehicle_plate_result import BoundingBox, DetectionResult

ROBOFLOW_SERVERLESS_URL = "https://serverless.roboflow.com"
MODEL_ID = "indian-license-plate-detection-6tmbr-ixo3p/2"

class LicensePlateDetector:
    """
    Detector client for Roboflow Serverless Object Detection model:
    'indian-license-plate-detection-6tmbr-ixo3p/2'
    
    Reads API key securely from environment variable: ROBOFLOW_API_KEY.
    NEVER hard-codes keys or falls back to synthetic plates.
    """

    def __init__(self, api_key: Optional[str] = None):
        self.api_key = api_key or os.environ.get("ROBOFLOW_API_KEY")

    def _call_roboflow_api(self, image_bgr: cv2.Mat) -> Dict[str, Any]:
        """
        Encodes image into JPEG and invokes Roboflow Serverless inference endpoint.
        Returns the parsed JSON response.
        """
        if not self.api_key:
            raise ValueError(
                "ROBOFLOW_API_KEY environment variable is not set. "
                "Please configure ROBOFLOW_API_KEY in your environment before running inference."
            )

        # Encode image to JPEG memory buffer
        success, encoded_img = cv2.imencode(".jpg", image_bgr)
        if not success:
            raise ValueError("Failed to encode image to JPEG buffer")

        # Roboflow Serverless API endpoint
        url = f"{ROBOFLOW_SERVERLESS_URL}/{MODEL_ID}?api_key={self.api_key}"

        # Roboflow accepts base64 or multipart/raw binary. Raw binary with multipart/image/jpeg is most robust
        headers = {
            "Content-Type": "application/x-www-form-urlencoded"
        }
        b64_data = base64.b64encode(encoded_img.tobytes()).decode("ascii")

        response = requests.post(
            url,
            data=b64_data,
            headers=headers,
            timeout=15
        )

        if response.status_code != 200:
            raise RuntimeError(
                f"Roboflow API returned HTTP {response.status_code}: {response.text}"
            )

        return response.json()

    def detect(
        self,
        image_path: str,
        output_crop_dir: Optional[str] = None,
        confidence_threshold: float = 0.35
    ) -> List[DetectionResult]:
        """
        Accepts an input vehicle image path and returns detected plates sorted by confidence.
        
        Cropped plates are cropped from the ORIGINAL input image (never the entire car).
        If multiple plates are detected, all detections are returned.
        If no plates are detected, returns an empty list.
        """
        if not os.path.exists(image_path):
            raise FileNotFoundError(f"Input image not found: {image_path}")

        image_bgr = cv2.imread(image_path)
        if image_bgr is None:
            raise ValueError(f"Could not read image file with OpenCV: {image_path}")

        img_h, img_w = image_bgr.shape[:2]

        # Call real serverless model
        api_response = self._call_roboflow_api(image_bgr)
        
        # Roboflow response structure for object detection:
        # {
        #   "predictions": [
        #       {
        #           "x": 420.5,
        #           "y": 310.2,
        #           "width": 140.0,
        #           "height": 45.0,
        #           "confidence": 0.942,
        #           "class": "license-plate",
        #           "class_id": 0
        #       }
        #   ],
        #   "image": {"width": 800, "height": 600}
        # }
        predictions = api_response.get("predictions", [])
        if not predictions and isinstance(api_response, list):
            predictions = api_response

        detections: List[DetectionResult] = []

        if output_crop_dir:
            os.makedirs(output_crop_dir, exist_ok=True)

        for idx, pred in enumerate(predictions):
            conf = float(pred.get("confidence", 0.0))
            if conf < confidence_threshold:
                continue

            cx = float(pred.get("x", 0.0))
            cy = float(pred.get("y", 0.0))
            w = float(pred.get("width", 0.0))
            h = float(pred.get("height", 0.0))
            cls_name = str(pred.get("class", "license-plate"))

            # Calculate pixel bounding box coordinates
            xmin = max(0, int(cx - w / 2))
            ymin = max(0, int(cy - h / 2))
            xmax = min(img_w, int(cx + w / 2))
            ymax = min(img_h, int(cy + h / 2))

            if xmax <= xmin or ymax <= ymin:
                continue

            bbox = BoundingBox(
                x=cx,
                y=cy,
                width=w,
                height=h,
                xmin=xmin,
                ymin=ymin,
                xmax=xmax,
                ymax=ymax
            )

            # Crop license plate from original image with slight margin
            pad_x = int(w * 0.02)
            pad_y = int(h * 0.02)
            crop_xmin = max(0, xmin - pad_x)
            crop_ymin = max(0, ymin - pad_y)
            crop_xmax = min(img_w, xmax + pad_x)
            crop_ymax = min(img_h, ymax + pad_y)

            crop_img = image_bgr[crop_ymin:crop_ymax, crop_xmin:crop_xmax]

            crop_path = None
            if output_crop_dir and crop_img.size > 0:
                base_name = os.path.splitext(os.path.basename(image_path))[0]
                crop_filename = f"{base_name}_plate_{idx + 1}.jpg"
                crop_path = os.path.join(output_crop_dir, crop_filename)
                cv2.imwrite(crop_path, crop_img)

            detections.append(DetectionResult(
                detected=True,
                confidence=round(conf, 4),
                bbox=bbox,
                crop_path=crop_path,
                class_name=cls_name
            ))

        # Sort all detected plates by confidence descending
        detections.sort(key=lambda d: d.confidence, reverse=True)
        return detections
