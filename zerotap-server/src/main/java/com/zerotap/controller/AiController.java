package com.zerotap.controller;

import com.zerotap.dto.*;
import com.zerotap.service.VertexAiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final VertexAiService vertexAiService;

    public AiController(VertexAiService vertexAiService) {
        this.vertexAiService = vertexAiService;
    }

    @PostMapping("/analyze-audio")
    public ResponseEntity<AiAudioAnalysisResponse> analyzeAudio(@RequestBody AiAudioAnalysisRequest request) {
        return ResponseEntity.ok(vertexAiService.analyzeAudio(request));
    }

    @PostMapping("/infer-context")
    public ResponseEntity<AiContextInferenceResponse> inferContext(@RequestBody AiContextInferenceRequest request) {
        return ResponseEntity.ok(vertexAiService.inferContext(request));
    }

    @PostMapping("/analyze-vehicle")
    public ResponseEntity<AiVehicleImageResponse> analyzeVehicle(@RequestBody AiVehicleImageRequest request) {
        return ResponseEntity.ok(vertexAiService.analyzeVehicleImage(request));
    }
}
