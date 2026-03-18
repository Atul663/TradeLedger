package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.GoogleToken;
import com.example.tradeLedger.repository.GoogleTokenRepository;
import com.example.tradeLedger.utils.CryptoUtil;
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

        // 🔐 ENCRYPT BEFORE SAVING
        token.setAccessToken(CryptoUtil.encrypt(accessToken));

        if (refreshToken != null) {
            token.setRefreshToken(CryptoUtil.encrypt(refreshToken));
        }

        token.setCreatedAt(System.currentTimeMillis());

        repository.save(token);
    }
}