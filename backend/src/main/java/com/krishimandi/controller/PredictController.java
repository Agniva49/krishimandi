package com.krishimandi.controller;

import com.krishimandi.service.PredictionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/predict")
public class PredictController {

    private static final Logger log = LoggerFactory.getLogger(PredictController.class);

    private final PredictionService predictionService;

    public PredictController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @PostMapping
    public ResponseEntity<?> predict(@AuthenticationPrincipal UserDetails userDetails,
                                     @RequestBody Map<String, Object> request) {
        String cropName = (String) request.get("cropName");
        String state    = (String) request.get("state");
        String district = (String) request.getOrDefault("district", "");
        int rangeDays   = Integer.parseInt(String.valueOf(request.getOrDefault("predictionRangeDays", 14)));
        String model    = (String) request.getOrDefault("modelPreference", "ensemble");

        log.info("Prediction request — crop: {}, state: {}, days: {}", cropName, state, rangeDays);

        if (cropName == null || state == null)
            return ResponseEntity.badRequest().body(Map.of("error", "cropName and state are required"));

        try {
            return ResponseEntity.ok(predictionService.predict(
                userDetails.getUsername(), cropName, state, district, rangeDays, model));
        } catch (Exception e) {
            log.error("Prediction failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Prediction service unavailable: " + e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(@AuthenticationPrincipal UserDetails userDetails,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(predictionService.getHistory(
            userDetails.getUsername(), PageRequest.of(page, Math.min(size, 50))));
    }

    @GetMapping("/history/{id}")
    public ResponseEntity<?> getPrediction(@AuthenticationPrincipal UserDetails userDetails,
                                           @PathVariable String id) {
        return predictionService.getPredictionById(id, userDetails.getUsername())
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/history/{id}")
    public ResponseEntity<?> deletePrediction(@AuthenticationPrincipal UserDetails userDetails,
                                              @PathVariable String id) {
        predictionService.deletePrediction(id, userDetails.getUsername());
        return ResponseEntity.ok(Map.of("message", "Prediction deleted"));
    }

    @GetMapping("/crops")
    public ResponseEntity<?> getSupportedCrops() {
        return ResponseEntity.ok(predictionService.getSupportedCrops());
    }

    @GetMapping("/market")
    public ResponseEntity<?> getMarketSnapshot(@RequestParam(required = false) String crop,
                                               @RequestParam(required = false) String state) {
        return ResponseEntity.ok(predictionService.getMarketSnapshot(crop, state));
    }

    @GetMapping("/analytics")
    public ResponseEntity<?> getAnalytics(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(predictionService.getUserAnalytics(userDetails.getUsername()));
    }

    @PutMapping("/history/{id}/actual")
    public ResponseEntity<?> updateActualPrice(@AuthenticationPrincipal UserDetails userDetails,
                                               @PathVariable String id,
                                               @RequestBody Map<String, Double> body) {
        predictionService.recordActualPrice(id, userDetails.getUsername(),
            body.getOrDefault("actualPrice", 0.0));
        return ResponseEntity.ok(Map.of("message", "Actual price recorded"));
    }
}
