package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.PlatformStrategyToggleResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

public interface PlatformStrategyToggleService {
    List<PlatformStrategyToggleResponse> getAllStrategies();
    ResponseEntity<?> getStrategy(String name);
    ResponseEntity<?> createStrategy(String name, Map<String, Object> config);
    ResponseEntity<?> toggleStrategy(String name);
    ResponseEntity<?> updateConfig(String name, Map<String, Object> config);
    ResponseEntity<?> deleteStrategy(String name);
}
