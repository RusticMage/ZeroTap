package com.zerotap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zerotap.dto.PlateAnalysisResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/vision")
public class VisionController {

    private static final Logger log = LoggerFactory.getLogger(VisionController.class);
    private static final String ROBOFLOW_URL = "https://serverless.roboflow.com/indian-license-plate-detection-6tmbr-ixo3p/2";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${roboflow.api.key:#{environment.ROBOFLOW_API_KEY}}")
    private String roboflowApiKey;

    // Pattern for Indian license plate formats
    private static final Pattern INDIAN_PLATE_PATTERN = Pattern.compile(
            "^(AN|AP|AR|AS|BR|CH|CG|DN|DD|DL|GA|GJ|HR|HP|JK|JH|KA|KL|LA|LD|MP|MH|MN|ML|MZ|NL|OD|OR|PY|PB|RJ|SK|TN|TS|TR|UP|UK|UA|WB|BH)(\\d{1,2})([A-Z]{0,3})(\\d{4})$"
    );

    @PostMapping(value = "/analyze-plate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlateAnalysisResponseDto> analyzePlate(
            @RequestPart("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.ok(PlateAnalysisResponseDto.negative());
        }

        // 1. Check if Roboflow API key is configured
        String apiKey = (roboflowApiKey != null && !roboflowApiKey.isBlank())
                ? roboflowApiKey
                : System.getenv("ROBOFLOW_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ROBOFLOW_API_KEY is not set. Cannot run remote plate detection.");
            return ResponseEntity.ok(PlateAnalysisResponseDto.negative());
        }

        try {
            byte[] imageBytes = file.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 2. Call Roboflow Serverless Detector
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String requestUrl = ROBOFLOW_URL + "?api_key=" + apiKey;
            HttpEntity<String> requestEntity = new HttpEntity<>(base64Image, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(requestUrl, requestEntity, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.warn("Roboflow returned status {}", response.getStatusCode());
                return ResponseEntity.ok(PlateAnalysisResponseDto.negative());
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode predictions = root.get("predictions");

            if (predictions == null || !predictions.isArray() || predictions.isEmpty()) {
                // Negative case: No license plate detected on image! (e.g. hand, face, random room)
                log.info("No license plate detected in image.");
                return ResponseEntity.ok(PlateAnalysisResponseDto.negative());
            }

            // Plate detected by Roboflow! Find prediction with highest confidence
            double bestConfidence = 0.0;
            JsonNode bestPrediction = null;
            for (JsonNode pred : predictions) {
                double conf = pred.path("confidence").asDouble(0.0);
                if (conf > bestConfidence) {
                    bestConfidence = conf;
                    bestPrediction = pred;
                }
            }

            if (bestConfidence < 0.35 || bestPrediction == null) {
                return ResponseEntity.ok(PlateAnalysisResponseDto.negative());
            }

            // 3. Invoke Python OCR pipeline if available on the system
            PlateAnalysisResponseDto ocrResult = runPythonPipelineOcr(file);
            if (ocrResult != null && ocrResult.isPlateDetected()) {
                ocrResult.setDetectorConfidence((float) bestConfidence);
                return ResponseEntity.ok(ocrResult);
            }

            // Fallback if Python OCR pipeline is unavailable: Roboflow confirmed plate presence
            PlateAnalysisResponseDto result = new PlateAnalysisResponseDto();
            result.setPlateDetected(true);
            result.setDetectorConfidence((float) bestConfidence);
            result.setConfidence((float) bestConfidence);
            result.setValidFormat(false);
            result.setRegistrationNumber(null); // No OCR fallback; strictly honest
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Plate analysis failed", e);
            return ResponseEntity.ok(PlateAnalysisResponseDto.negative());
        }
    }

    private PlateAnalysisResponseDto runPythonPipelineOcr(MultipartFile file) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("plate_analysis_", ".jpg");
            file.transferTo(tempFile.toFile());

            // Run: python detect_plate.py <tempFile> --json
            ProcessBuilder pb = new ProcessBuilder(
                    "python",
                    "detect_plate.py",
                    tempFile.toAbsolutePath().toString(),
                    "--json"
            );
            pb.directory(new File("..").getCanonicalFile()); // ZeroTap root
            Process process = pb.start();

            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();

            if (output.trim().startsWith("{")) {
                JsonNode json = objectMapper.readTree(output);
                PlateAnalysisResponseDto dto = new PlateAnalysisResponseDto();
                dto.setPlateDetected(json.path("plateDetected").asBoolean(false));
                String reg = json.path("registrationNumber").isNull() ? null : json.path("registrationNumber").asText(null);
                dto.setRegistrationNumber(reg);
                dto.setDetectorConfidence((float) json.path("detectorConfidence").asDouble(0.0));
                dto.setOcrConfidence((float) json.path("ocrConfidence").asDouble(0.0));
                dto.setConfidence(dto.getOcrConfidence() > 0 ? dto.getOcrConfidence() : dto.getDetectorConfidence());
                dto.setRawOcrText(json.path("rawOcrText").isNull() ? null : json.path("rawOcrText").asText(null));
                dto.setValidFormat(json.path("validFormat").asBoolean(false));
                return dto;
            }
        } catch (Exception e) {
            log.debug("Python pipeline execution skipped or failed: {}", e.getMessage());
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
        }
        return null;
    }
}
