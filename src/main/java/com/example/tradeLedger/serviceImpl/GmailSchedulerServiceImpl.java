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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

        ExecutorService executor = Executors.newFixedThreadPool(5);

        for (UserDetails user : users) {
            executor.submit(() -> {
                try {
                    if (user.isRevoked()) {
                        return; // Instead of continue inside lambda
                    }

                    if (user.getRefreshToken() == null) {
                        log.warn("No refresh token for user: {}", user.getEmail());
                        return;
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
            });
        }

        executor.shutdown();
        try {
            // Give threads a maximum of 30 minutes to finish processing PDFs
            if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                log.warn("Scheduler timed out waiting for all users to process");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Email processing interrupted", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("Scheduler finished");
    }
}
