package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.GoogleToken;
import com.example.tradeLedger.repository.GoogleTokenRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class GoogleTokenService {

    private final GoogleTokenRepository repository;

    public GoogleTokenService(GoogleTokenRepository repository) {
        this.repository = repository;
    }

    public void saveOrUpdateToken(String email, String accessToken, String refreshToken) {

        GoogleToken token = repository.findByEmail(email)
                .orElse(new GoogleToken());

        token.setEmail(email);
        token.setAccessToken(accessToken);

        if (refreshToken != null) {
            token.setRefreshToken(refreshToken);
        }

        token.setCreatedAt(System.currentTimeMillis());

        repository.save(token);
    }}