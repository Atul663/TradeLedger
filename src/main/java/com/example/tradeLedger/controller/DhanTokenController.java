package com.example.tradeLedger.controller;


import com.example.tradeLedger.dto.AccessTokenResponse;
import com.example.tradeLedger.entity.DhanAccessToken;
import com.example.tradeLedger.exception.TokenNotFoundException;
import com.example.tradeLedger.exception.TokenRenewalException;
import com.example.tradeLedger.service.DhanTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dhan/token")
public class DhanTokenController {

    private final DhanTokenService service;

    public DhanTokenController(DhanTokenService service) {
        this.service = service;
    }

    // 1. Renew: calls Dhan, stores new token, returns it
    @PostMapping("/renew")
    public ResponseEntity<?> renew() {
        try {
            DhanAccessToken t = service.renewAndStore();
            return ResponseEntity.ok(
                    new AccessTokenResponse(t.getAccessToken(), t.getDhanClientId(), t.getExpiryTime()));
        } catch (TokenRenewalException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "TOKEN_RENEWAL_FAILED", "message", ex.getMessage()));
        }
    }

    // 2. Get: reads latest from DB only, never calls Dhan
    @GetMapping
    public ResponseEntity<?> get() {
        try {
            DhanAccessToken t = service.getLatestToken();
            return ResponseEntity.ok(
                    new AccessTokenResponse(t.getAccessToken(), t.getDhanClientId(), t.getExpiryTime()));
        } catch (TokenNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "TOKEN_NOT_FOUND", "message", ex.getMessage()));
        }
    }
}