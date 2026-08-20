package com.example.tradeLedger.dto;

import java.util.UUID;

/**
 * Reference data: a broker the platform can place orders through.
 *
 * {@code authType} is what a credential form should switch on - it says which of
 * the fields on {@code BrokerCredentialRequest} this broker actually needs.
 */
public record BrokerResponse(
        UUID id,
        String code,
        String name,
        String description,
        String apiBaseUrl,
        String authType,
        boolean active) {
}
