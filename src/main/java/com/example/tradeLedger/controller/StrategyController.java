package com.example.tradeLedger.controller;

import com.example.tradeLedger.service.StrategyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/strategy")
public class StrategyController {

    private final StrategyService strategyService;

    public StrategyController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    @GetMapping
    public ResponseEntity<?> getAllStrategies() {
        return ResponseEntity.ok(strategyService.getAllStrategies());
    }

    @GetMapping("/{name}")
    public ResponseEntity<?> getStrategy(@PathVariable String name) {
        return strategyService.getStrategy(name);
    }

    @PostMapping("/{name}")
    public ResponseEntity<?> createStrategy(@PathVariable String name,
                                            @RequestBody(required = false) Map<String, Object> config) {
        return strategyService.createStrategy(name, config);
    }

    @PutMapping("/{name}/toggle")
    public ResponseEntity<?> toggleStrategy(@PathVariable String name) {
        return strategyService.toggleStrategy(name);
    }

    @PutMapping("/{name}/config")
    public ResponseEntity<?> updateConfig(@PathVariable String name,
                                          @RequestBody Map<String, Object> config) {
        return strategyService.updateConfig(name, config);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<?> deleteStrategy(@PathVariable String name) {
        return strategyService.deleteStrategy(name);
    }
}
