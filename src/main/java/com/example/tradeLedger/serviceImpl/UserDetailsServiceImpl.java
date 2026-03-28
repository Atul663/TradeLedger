package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.UserDetails;
import com.example.tradeLedger.repository.UserDetailsRepository;
import com.example.tradeLedger.service.UserDetailsService;
import com.example.tradeLedger.utils.CryptoUtil;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserDetailsRepository repository;

    public UserDetailsServiceImpl(UserDetailsRepository repository) {
        this.repository = repository;
    }

    @Override
    public void saveOrUpdateToken(String email, String accessToken, String refreshToken) {

        UserDetails token = repository.findByEmail(email)
                .orElse(new UserDetails());

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
