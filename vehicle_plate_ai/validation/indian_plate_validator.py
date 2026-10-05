import re
from typing import Tuple

# Standard 28 Indian States & 8 Union Territories codes
INDIAN_STATE_CODES = {
    "AN", "AP", "AR", "AS", "BR", "CH", "CG", "CH", "DN", "DD",
    "DL", "GA", "GJ", "HR", "HP", "JK", "JH", "KA", "KL", "LA",
    "LD", "MP", "MH", "MN", "ML", "MZ", "NL", "OD", "OR", "PY",
    "PB", "RJ", "SK", "TN", "TS", "TR", "UP", "UK", "UA", "WB",
    "BH"  # Bharat Series
}

# Standard format: State(2) + RTO(2 digits) + Series(0-3 letters) + Number(4 digits)
# Example: TN07CB1234, KA05MH8821, DL01A1234, MH121234, DL8CAA1111 (single digit RTO or 2 digits)
STANDARD_INDIAN_PLATE_PATTERN = re.compile(
    r"^([A-Z]{2})([0-9]{1,2})([A-Z]{0,3})([0-9]{4})$"
)

# Bharat (BH) series format: YY BH #### XX (e.g. 22BH1234AA)
BHARAT_SERIES_PATTERN = re.compile(
    r"^([0-9]{2})(BH)([0-9]{4})([A-Z]{1,2})$"
)

# Vintage/Diplomatic/Military formats
# CD/CC diplomatic: 22CD1234, 12CC3456
# Military format: typically has upward arrow or starts with 2 digits followed by letter
DIPLOMATIC_PATTERN = re.compile(
    r"^([0-9]{1,3})(CD|CC)([0-9]{1,4})$"
)

def normalize_plate_text(raw_text: str) -> str:
    """
    Cleans raw OCR text:
    - Removes spaces, tabs, newlines
    - Strips punctuation and special characters
    - Converts to uppercase
    - Removes common leading noise like 'IND' (HSRP logo)
    """
    if not raw_text:
        return ""
    
    # Strip whitespace & special characters
    clean = re.sub(r"[^A-Za-z0-9]", "", raw_text).upper()
    
    # In High Security Registration Plates (HSRP), the word 'IND' is frequently read
    # at the extreme left. If the cleaned text begins with IND and the rest matches an Indian state code,
    # strip IND cleanly.
    if clean.startswith("IND") and len(clean) > 5:
        potential_state = clean[3:5]
        if potential_state in INDIAN_STATE_CODES:
            clean = clean[3:]

    return clean

def validate_indian_plate(text: str) -> Tuple[bool, str]:
    """
    Validates whether the normalized text conforms to a legitimate Indian vehicle registration plate.
    Returns:
        (is_valid, matched_format_description)
    """
    if not text or len(text) < 7 or len(text) > 12:
        return False, "invalid_length"

    # Test standard pattern (State + RTO + Series + 4 digits)
    std_match = STANDARD_INDIAN_PLATE_PATTERN.match(text)
    if std_match:
        state_code = std_match.group(1)
        if state_code in INDIAN_STATE_CODES:
            return True, "standard_state_series"
        else:
            # Matches pattern structure but state code is invalid
            return False, f"unrecognized_state_code_{state_code}"

    # Test Bharat series (e.g. 21BH1234AA)
    bh_match = BHARAT_SERIES_PATTERN.match(text)
    if bh_match:
        return True, "bharat_bh_series"

    # Test diplomatic format
    dip_match = DIPLOMATIC_PATTERN.match(text)
    if dip_match:
        return True, "diplomatic_series"

    return False, "unmatched_pattern"
