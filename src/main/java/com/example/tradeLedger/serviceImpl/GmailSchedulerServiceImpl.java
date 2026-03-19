package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.UserDetails;
import com.example.tradeLedger.repository.UserDetailsRepository;
import com.example.tradeLedger.service.GmailSchedulerService;
import com.example.tradeLedger.service.GmailService;
import com.example.tradeLedger.utils.CryptoUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GmailSchedulerServiceImpl implements GmailSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(GmailSchedulerServiceImpl.class);

    private final UserDetailsRepository userDetailsRepository;
    private final GmailService gmailService;

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    public GmailSchedulerServiceImpl(UserDetailsRepository userDetailsRepository,
                                     GmailService gmailService) {
        this.userDetailsRepository = userDetailsRepository;
        this.gmailService = gmailService;
    }

    @Override
    public void processEmails() {
        log.info("Scheduler started...");

        List<UserDetails> users = userDetailsRepository.findAll();
        if (users.isEmpty()) {
            log.info("No users found");
            return;
        }

        for (UserDetails user : users) {
            try {
                if (user.isRevoked()) {
                    continue;
                }

                if (user.getRefreshToken() == null) {
                    log.warn("No refresh token for user: {}", user.getEmail());
                    continue;
                }

                String refreshToken = CryptoUtil.decrypt(user.getRefreshToken());
                String newAccessToken = new GoogleRefreshTokenRequest(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        refreshToken,
                        clientId,
                        clientSecret
                ).execute().getAccessToken();

                user.setAccessToken(CryptoUtil.encrypt(newAccessToken));
                userDetailsRepository.save(user);

                gmailService.readEmailsWithAttachments(
                        user.getEmail(),
                        "atulprogramming2001@gmail.com"
                );

                log.info("Successfully processed user: {}", user.getEmail());
            } catch (Exception e) {
                log.error("Error processing user: {}", user.getEmail(), e);
            }
        }

        log.info("Scheduler finished");
    }
}
