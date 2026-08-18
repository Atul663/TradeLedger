package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.GoogleAuthToken;
import com.example.tradeLedger.repository.GoogleAuthTokenRepository;
import com.example.tradeLedger.service.GoogleAuthTokenService;
import com.example.tradeLedger.utils.CryptoUtil;
import org.springframework.stereotype.Service;

@Service
public class GoogleAuthTokenServiceImpl implements GoogleAuthTokenService {

    private final GoogleAuthTokenRepository repository;

    public GoogleAuthTokenServiceImpl(GoogleAuthTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    public void saveOrUpdateToken(String email, String accessToken, String refreshToken) {

        GoogleAuthToken token = repository.findByEmail(email)
                .orElse(new GoogleAuthToken());

        token.setEmail(email);
        token.setRevoked(false);

        // 🔐 ENCRYPT BEFORE SAVING
        token.setAccessToken(CryptoUtil.encrypt(accessToken));

        if (refreshToken != null) {
            token.setRefreshToken(CryptoUtil.encrypt(refreshToken));
        }

        token.setCreatedAt(System.currentTimeMillis());

        repository.save(token);
    }
}
