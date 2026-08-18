package com.example.tradeLedger.service;


import com.example.tradeLedger.dto.DhanRenewResponse;
import com.example.tradeLedger.entity.DhanAccessToken;
import com.example.tradeLedger.exception.TokenNotFoundException;
import com.example.tradeLedger.exception.TokenRenewalException;
import com.example.tradeLedger.repository.DhanAccessTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;

@Service
public class DhanTokenService {

    private final DhanAccessTokenRepository repository;
    private final RestTemplate restTemplate;

    @Value("${dhan.renew-url:https://api.dhan.co/v2/renewToken}")
    private String renewUrl;
    @Value("${dhan.partner-id}")
    private String partnerId;
    @Value("${dhan.partner-secret}")
    private String partnerSecret;
    @Value("${dhan.consent-id}")
    private String consentId;

    public DhanTokenService(DhanAccessTokenRepository repository, RestTemplate restTemplate) {
        this.repository = repository;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public DhanAccessToken renewAndStore() {
        DhanRenewResponse renewed = callDhanRenew();

        if (renewed == null || !StringUtils.hasText(renewed.getAccessToken())) {
            // Do NOT touch the DB — existing token stays intact
            throw new TokenRenewalException("Dhan returned no access token; existing token preserved.");
        }

        // Update in place if a row exists, otherwise create a new one
        DhanAccessToken entity = repository.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(DhanAccessToken::new);

        entity.setAccessToken(renewed.getAccessToken());
        entity.setDhanClientId(renewed.getDhanClientId());
        entity.setExpiryTime(parseExpiry(renewed.getExpiryTime()));

        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public DhanAccessToken getLatestToken() {
        return repository.findFirstByOrderByUpdatedAtDesc()
                .orElseThrow(() -> new TokenNotFoundException("No access token found in database."));
    }

    private DhanRenewResponse callDhanRenew() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("partner_id", partnerId);
        headers.set("partner_secret", partnerSecret);

        String body = "{\"consentId\":\"" + consentId + "\"}";
        HttpEntity<String> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<DhanRenewResponse> response =
                    restTemplate.postForEntity(renewUrl, request, DhanRenewResponse.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new TokenRenewalException("Dhan renewal failed with status: " + response.getStatusCode());
            }
            return response.getBody();
        } catch (RestClientException ex) {
            throw new TokenRenewalException("Error calling Dhan renewal endpoint.", ex);
        }
    }

    private OffsetDateTime parseExpiry(String expiry) {
        if (!StringUtils.hasText(expiry)) return null;
        try {
            return OffsetDateTime.parse(expiry);
        } catch (Exception e) {
            return null; // don't fail the whole renewal over an unparseable expiry
        }
    }
}