package com.example.tradeLedger.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DhanRenewResponse {
    @JsonProperty("accessToken")
    private String accessToken;
    @JsonProperty("dhanClientId")
    private String dhanClientId;
    @JsonProperty("expiryTime")
    private String expiryTime; // ISO-8601 string from Dhan

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getDhanClientId() { return dhanClientId; }
    public void setDhanClientId(String dhanClientId) { this.dhanClientId = dhanClientId; }
    public String getExpiryTime() { return expiryTime; }
    public void setExpiryTime(String expiryTime) { this.expiryTime = expiryTime; }
}