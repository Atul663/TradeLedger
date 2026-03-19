package com.example.tradeLedger.service;

public interface UserDetailsService {

    void saveOrUpdateToken(String email, String accessToken, String refreshToken);
}
