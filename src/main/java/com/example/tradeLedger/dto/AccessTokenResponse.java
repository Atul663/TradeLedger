package com.example.tradeLedger.dto;

import java.time.OffsetDateTime;

public class AccessTokenResponse {

    private String accessToken;
    private String dhanClientId;
    private OffsetDateTime expiryTime;

    public AccessTokenResponse(String accessToken, String dhanClientId, OffsetDateTime expiryTime) {
        this.accessToken = accessToken;
        this.dhanClientId = dhanClientId;
        this.expiryTime = expiryTime;
    }

    public String getAccessToken() { return accessToken; }
    public String getDhanClientId() { return dhanClientId; }
    public OffsetDateTime getExpiryTime() { return expiryTime; }
}
