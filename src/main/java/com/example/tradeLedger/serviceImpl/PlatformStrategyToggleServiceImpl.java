package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.PlatformStrategyToggleResponse;
import com.example.tradeLedger.entity.PlatformStrategyToggle;
import com.example.tradeLedger.repository.PlatformStrategyToggleRepository;
import com.example.tradeLedger.service.PlatformStrategyToggleService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlatformStrategyToggleServiceImpl implements PlatformStrategyToggleService {

    private final PlatformStrategyToggleRepository repository;
    private final ObjectMapper objectMapper;

    public PlatformStrategyToggleServiceImpl(PlatformStrategyToggleRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PlatformStrategyToggleResponse> getAllStrategies() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ResponseEntity<?> getStrategy(String name) {
        return repository.findByStrategyName(name.toUpperCase())
                .map(s -> ResponseEntity.ok(toResponse(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<?> createStrategy(String name, Map<String, Object> config) {
        String key = name.toUpperCase();
        if (repository.findByStrategyName(key).isPresent()) {
            return ResponseEntity.badRequest().body("Strategy toggle already exists: " + key);
        }
        PlatformStrategyToggle strategy = new PlatformStrategyToggle();
        strategy.setStrategyName(key);
        strategy.setEnabled(false);
        strategy.setConfigJson(toJson(config != null ? config : new LinkedHashMap<>()));
        strategy.setUpdatedAt(System.currentTimeMillis());
        repository.save(strategy);
        return ResponseEntity.ok(toResponse(strategy));
    }

    @Override
    public ResponseEntity<?> toggleStrategy(String name) {
        PlatformStrategyToggle strategy = repository.findByStrategyName(name.toUpperCase()).orElse(null);
        if (strategy == null) return ResponseEntity.notFound().build();

        boolean enabling = !strategy.isEnabled();

        if (enabling) {
            repository.findAll().forEach(s -> {
                if (!s.getStrategyName().equals(name.toUpperCase())) {
                    s.setEnabled(false);
                    s.setUpdatedAt(System.currentTimeMillis());
                    repository.save(s);
                }
            });
        }

        strategy.setEnabled(enabling);
        strategy.setUpdatedAt(System.currentTimeMillis());
        repository.save(strategy);
        return ResponseEntity.ok(toResponse(strategy));
    }

    @Override
    public ResponseEntity<?> updateConfig(String name, Map<String, Object> config) {
        PlatformStrategyToggle strategy = repository.findByStrategyName(name.toUpperCase()).orElse(null);
        if (strategy == null) return ResponseEntity.notFound().build();

        Map<String, Object> existing = fromJson(strategy.getConfigJson());
        existing.putAll(config);
        strategy.setConfigJson(toJson(existing));
        strategy.setUpdatedAt(System.currentTimeMillis());
        repository.save(strategy);
        return ResponseEntity.ok(toResponse(strategy));
    }

    @Override
    public ResponseEntity<?> deleteStrategy(String name) {
        PlatformStrategyToggle strategy = repository.findByStrategyName(name.toUpperCase()).orElse(null);
        if (strategy == null) return ResponseEntity.notFound().build();
        repository.delete(strategy);
        return ResponseEntity.ok("Deleted: " + name.toUpperCase());
    }

    private PlatformStrategyToggleResponse toResponse(PlatformStrategyToggle s) {
        return new PlatformStrategyToggleResponse(s.getStrategyName(), s.isEnabled(), fromJson(s.getConfigJson()), s.getUpdatedAt());
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize config", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            if (json == null || json.isBlank()) return new LinkedHashMap<>();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
