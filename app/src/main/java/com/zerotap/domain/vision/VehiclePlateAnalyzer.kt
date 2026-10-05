package com.zerotap.domain.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.regex.Pattern
import kotlin.coroutines.resume

/**
 * Encapsulates an image input destined for vision analysis.
 */
data class ImageInput(
    val file: File,
    val mimeType: String = "image/jpeg"
)

/**
 * Structured analysis result from the vehicle plate vision model.
 */
data class VehiclePlateResult(
    val plateDetected: Boolean,
    val registrationNumber: String?,
    val confidence: Float
)

/**
 * Clean interface abstraction for the vehicle number-plate vision model.
 */
interface VehiclePlateAnalyzer {
    suspend fun analyze(image: ImageInput): VehiclePlateResult
}

/**
 * Normalizes vehicle registration strings:
 * Removes spaces, dashes, dots, and converts to uppercase.
 * e.g., "tn 09 ab 1234" -> "TN09AB1234"
 */
fun normalizePlateNumber(raw: String): String {
    return raw.replace(Regex("[^A-Za-z0-9]"), "").uppercase(Locale.US)
}

/**
 * High-precision Indian License Plate Parser & Sanitizer:
 * Validates against the 36 Indian States and Union Territories + Bharat (BH) Series.
 * Eliminates OCR trailing artifacts (borders, screws, grill shadows) and enforces strict character limits.
 */
object IndianPlateParser {

    val INDIAN_STATE_CODES = setOf(
        "AN", "AP", "AR", "AS", "BR", "CH", "CG", "DN", "DD", "DL", "GA", "GJ", "HR", "HP", "JK", "JH",
        "KA", "KL", "LA", "LD", "MP", "MH", "MN", "ML", "MZ", "NL", "OD", "OR", "PY", "PB", "RJ", "SK",
        "TN", "TS", "TR", "UP", "UK", "UA", "WB"
    )

    // Bharat Series: (YY) BH (4 digits) (1-2 letters)
    private val BHARAT_SERIES_REGEX = Pattern.compile(
        "([0-9]{2})\\s*BH\\s*([0-9]{4})\\s*([A-Z]{1,2})"
    )

    // Standard State Series: State(2) RTO(1-2 digits) Series(0-3 letters) Number(4 digits)
    // Strictly ends at the 4 digits!
    private val STANDARD_PLATE_REGEX = Pattern.compile(
        "([A-Z]{2})\\s*([0-9]{1,2})\\s*([A-Z]{0,3})\\s*([0-9]{4})"
    )

    // Fuzzy matcher for optical confusions (e.g. 'O' for 0, 'I' for 1, 'S' for 5)
    private val FUZZY_STANDARD_REGEX = Pattern.compile(
        "([A-Z0-9]{2})\\s*([A-Z0-9]{1,2})\\s*([A-Z0-9]{0,3})\\s*([A-Z0-9]{4})"
    )

    fun parse(rawText: String): String? {
        if (rawText.isBlank()) return null

        val uppercase = rawText.uppercase(Locale.US)

        // Strip HSRP logo "IND" prefix if present
        val withoutInd = uppercase
            .replace(Regex("^\\s*I\\s*N\\s*D\\s*"), "")
            .replace(Regex("\\bIND\\b"), "")
            .trim()

        val condensed = withoutInd.replace(Regex("[^A-Za-z0-9]"), "")

        // 1. Bharat Series Check (e.g., 22 BH 6517 A, 22 BH 6517 LA)
        val bhMatcher = BHARAT_SERIES_REGEX.matcher(withoutInd)
        val bhMatcherCondensed = BHARAT_SERIES_REGEX.matcher(condensed)
        val activeBhMatcher = when {
            bhMatcher.find() -> bhMatcher
            bhMatcherCondensed.find() -> bhMatcherCondensed
            else -> null
        }

        if (activeBhMatcher != null) {
            val yy = activeBhMatcher.group(1) ?: ""
            val num = activeBhMatcher.group(2) ?: ""
            var series = activeBhMatcher.group(3) ?: ""

            // Fine-tuning fix: In Bharat series, a trailing artifact from the digit '7' or plate border
            // often prefixes an 'L' or 'I' to the series letter (e.g. 'LA' instead of 'A').
            // Clean valid series letter:
            if (series.length == 2 && (series[0] == 'L' || series[0] == 'I') && series[1] in 'A'..'Z') {
                series = series[1].toString()
            }
            return "${yy}BH${num}${series}"
        }

        // 2. Standard State Check (e.g., KA 05 MH 8821, TN 07 CB 1234)
        val stdMatcher = STANDARD_PLATE_REGEX.matcher(withoutInd)
        val stdMatcherCondensed = STANDARD_PLATE_REGEX.matcher(condensed)
        val activeStdMatcher = when {
            stdMatcher.find() -> stdMatcher
            stdMatcherCondensed.find() -> stdMatcherCondensed
            else -> null
        }

        if (activeStdMatcher != null) {
            val state = activeStdMatcher.group(1) ?: ""
            val rto = activeStdMatcher.group(2) ?: ""
            val series = activeStdMatcher.group(3) ?: ""
            val num = activeStdMatcher.group(4) ?: ""

            if (INDIAN_STATE_CODES.contains(state)) {
                val rtoFormatted = if (rto.length == 1) "0$rto" else rto
                // Strictly bounded: State + RTO + Series + 4 digits. Any characters after the 4 digits are dropped.
                return "$state$rtoFormatted$series$num"
            }
        }

        // 3. Positional OCR Confusion Correction (e.g. OS for 05, O7 for 07, I234 for 1234)
        val fuzzyMatcher = FUZZY_STANDARD_REGEX.matcher(withoutInd)
        val fuzzyMatcherCondensed = FUZZY_STANDARD_REGEX.matcher(condensed)
        val activeFuzzyMatcher = when {
            fuzzyMatcher.find() -> fuzzyMatcher
            fuzzyMatcherCondensed.find() -> fuzzyMatcherCondensed
            else -> null
        }

        if (activeFuzzyMatcher != null) {
            var state = activeFuzzyMatcher.group(1) ?: ""
            var rto = activeFuzzyMatcher.group(2) ?: ""
            var series = fuzzyMatcher.group(3) ?: ""
            var num = fuzzyMatcher.group(4) ?: ""

            // Fix state code: letters only
            state = state.replace('0', 'O').replace('1', 'I').replace('8', 'B')

            if (INDIAN_STATE_CODES.contains(state)) {
                // Fix RTO: digits only
                rto = rto.replace('O', '0').replace('I', '1').replace('L', '1')
                    .replace('Z', '2').replace('S', '5').replace('B', '8')

                // Fix series: letters only
                series = series.replace('0', 'O').replace('1', 'I').replace('8', 'B')

                // Fix number: digits only
                num = num.replace('O', '0').replace('I', '1').replace('L', '1')
                    .replace('Z', '2').replace('S', '5').replace('B', '8')

                val rtoFormatted = if (rto.length == 1) "0$rto" else rto
                if (num.length == 4 && num.all { it.isDigit() }) {
                    return "$state$rtoFormatted$series$num"
                }
            }
        }

        return null
    }
}

/**
 * Real On-Device ML Kit Text Recognition Analyzer:
 * 
 * - Multi-pass analysis: evaluates line-level candidates first (preserving plate structure),
 *   then element sequences, then blocks.
 * - Applies IndianPlateParser to sanitize OCR confusions and truncate false trailing characters.
 * - Negative cases (hand, person, random object, empty room) yield NO plate match (confidence: 0.0, detected: false).
 */
class OnDeviceMLKitPlateAnalyzer : VehiclePlateAnalyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun analyze(image: ImageInput): VehiclePlateResult = withContext(Dispatchers.Default) {
        if (!image.file.exists() || image.file.length() == 0L) {
            return@withContext VehiclePlateResult(
                plateDetected = false,
                registrationNumber = null,
                confidence = 0.0f
            )
        }

        val bitmap = BitmapFactory.decodeFile(image.file.absolutePath)
            ?: return@withContext VehiclePlateResult(
                plateDetected = false,
                registrationNumber = null,
                confidence = 0.0f
            )

        // Pass 1: Full image
        val resultPass1 = runOcrOnBitmap(bitmap)
        if (resultPass1.plateDetected) {
            return@withContext resultPass1
        }

        // Pass 2: Center crop 75% region (users naturally center license plates in viewfinder;
        // this removes headlight glares, bumper frame borders, and dealership stickers)
        try {
            val cropW = (bitmap.width * 0.75).toInt()
            val cropH = (bitmap.height * 0.75).toInt()
            val cropX = (bitmap.width - cropW) / 2
            val cropY = (bitmap.height - cropH) / 2
            val centerCropBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
            val resultPass2 = runOcrOnBitmap(centerCropBitmap)
            if (resultPass2.plateDetected) {
                return@withContext resultPass2
            }
        } catch (_: Exception) {}

        // Negative case: No license plate found
        VehiclePlateResult(
            plateDetected = false,
            registrationNumber = null,
            confidence = 0.0f
        )
    }

    private suspend fun runOcrOnBitmap(bmp: Bitmap): VehiclePlateResult {
        val inputImage = InputImage.fromBitmap(bmp, 0)

        val visionText = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text?> { continuation ->
            recognizer.process(inputImage)
                .addOnSuccessListener { text ->
                    if (continuation.isActive) continuation.resume(text)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        } ?: return VehiclePlateResult(plateDetected = false, registrationNumber = null, confidence = 0.0f)

        // Priority 1: Individual line-level evaluation (license plates sit on a single line)
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val plate = IndianPlateParser.parse(line.text)
                if (plate != null) {
                    return VehiclePlateResult(
                        plateDetected = true,
                        registrationNumber = plate,
                        confidence = 0.95f
                    )
                }

                // Try joining line elements with space
                val elementsText = line.elements.joinToString(" ") { it.text }
                val plateFromElements = IndianPlateParser.parse(elementsText)
                if (plateFromElements != null) {
                    return VehiclePlateResult(
                        plateDetected = true,
                        registrationNumber = plateFromElements,
                        confidence = 0.95f
                    )
                }
            }
        }

        // Priority 2: Block-level evaluation (for 2-line number plates, e.g. two-wheelers/SUVs)
        for (block in visionText.textBlocks) {
            val plate = IndianPlateParser.parse(block.text)
            if (plate != null) {
                return VehiclePlateResult(
                    plateDetected = true,
                    registrationNumber = plate,
                    confidence = 0.93f
                )
            }
        }

        // Priority 3: Full text evaluation
        val fullPlate = IndianPlateParser.parse(visionText.text)
        if (fullPlate != null) {
            return VehiclePlateResult(
                plateDetected = true,
                registrationNumber = fullPlate,
                confidence = 0.91f
            )
        }

        return VehiclePlateResult(
            plateDetected = false,
            registrationNumber = null,
            confidence = 0.0f
        )
    }
}
