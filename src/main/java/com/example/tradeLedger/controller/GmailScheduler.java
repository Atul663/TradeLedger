package com.example.tradeLedger.controller;

import com.example.tradeLedger.entity.GoogleToken;
import com.example.tradeLedger.repository.GoogleTokenRepository;
import com.example.tradeLedger.serviceImpl.GmailService;
import com.example.tradeLedger.utils.CryptoUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GmailScheduler {

    private final GoogleTokenRepository tokenRepository;
    @Autowired
    GmailService gmailService;

    @Value("${google.client.id}")
    private String CLIENT_ID;

    @Value("${google.client.secret}")
    private String CLIENT_SECRET;

    public GmailScheduler(GoogleTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Kolkata")
    public void runScheduler() {

        List<GoogleToken> users = tokenRepository.findAll();

        for (GoogleToken user : users) {
            try {
                String refreshToken = CryptoUtil.decrypt(user.getRefreshToken());

                String newAccessToken = new GoogleRefreshTokenRequest(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        refreshToken,
                        CLIENT_ID,
                        CLIENT_SECRET
                ).execute().getAccessToken();

                user.setAccessToken(CryptoUtil.encrypt(newAccessToken));
                tokenRepository.save(user);

                System.out.println("Refreshed token for: " + user.getEmail());

                // 👉 call Gmail API per user here
                gmailService.readEmailsWithAttachments(newAccessToken, user.getEmail());

            } catch (Exception e) {
                System.out.println("Error for user: " + user.getEmail());
                e.printStackTrace();
            }
        }
    }
}