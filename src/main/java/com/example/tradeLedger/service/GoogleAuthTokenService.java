package com.example.tradeLedger.service;

public interface GoogleAuthTokenService {

    void saveOrUpdateToken(String email, String accessToken, String refreshToken);
}
