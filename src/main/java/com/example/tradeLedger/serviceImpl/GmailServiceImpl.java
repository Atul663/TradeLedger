package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.UserDetails;
import com.example.tradeLedger.repository.UserDetailsRepository;
import com.example.tradeLedger.service.GmailService;
import com.example.tradeLedger.service.PdfProcessingService;
import com.example.tradeLedger.utils.CryptoUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

@Service
public class GmailServiceImpl implements GmailService {

    private final UserDetailsRepository userDetailsRepository;

    private final PdfProcessingService pdfProcessingService;

    @Value("${google.client.id}")
    private String CLIENT_ID;

    @Value("${google.client.secret}")
    private String CLIENT_SECRET;

    public GmailServiceImpl(UserDetailsRepository userDetailsRepository,
                            PdfProcessingService pdfProcessingService) {
        this.userDetailsRepository = userDetailsRepository;
        this.pdfProcessingService = pdfProcessingService;
    }

    // ✅ PUBLIC METHOD (used by controller)
    @Override
    public void readEmailsWithAttachments(String userEmail, String senderEmail) throws Exception {

        String normalizedEmail = userEmail.trim().toLowerCase();

        System.out.println("USER EMAIL : {" + normalizedEmail + "}");

        UserDetails userDetails = userDetailsRepository.findByEmail(normalizedEmail)
                .orElse(null);

        if (userDetails == null) {
            System.out.println("❌ User not found for: {" + normalizedEmail + "}");
            return; // 🔥 DO NOT throw
        }

        String accessToken = getValidAccessToken(userDetails);

        try {
            fetchEmailsWithAttachments(accessToken, senderEmail, userDetails.getPanCard());

        } catch (Exception e) {

            System.out.println("Access token expired, refreshing...");

            String newAccessToken = refreshAccessToken(
                    CryptoUtil.decrypt(userDetails.getRefreshToken())
            );

            System.out.println("NEW ACCESS TOKEN : " + newAccessToken);

            userDetails.setAccessToken(CryptoUtil.encrypt(newAccessToken));
            userDetailsRepository.save(userDetails);

            fetchEmailsWithAttachments(newAccessToken, senderEmail, userDetails.getPanCard());
        }
    }

    // ✅ CORE LOGIC
    private void fetchEmailsWithAttachments(String accessToken, String senderEmail, String panCard) throws Exception {

        Gmail service = getGmailService(accessToken);

        ListMessagesResponse response = service.users().messages().list("me")
                .setQ("from:" + senderEmail + " has:attachment filename:pdf")
                .execute();

        if (response.getMessages() == null) {
            System.out.println("No emails found");
            return;
        }

        for (Message msg : response.getMessages()) {

            Message message = service.users().messages()
                    .get("me", msg.getId())
                    .execute();

            MessagePart payload = message.getPayload();

            if (payload.getParts() == null) continue;

            for (MessagePart part : payload.getParts()) {

                String fileName = part.getFilename();

                if (fileName != null && fileName.endsWith(".pdf")) {

                    String attachmentId = part.getBody().getAttachmentId();

                    MessagePartBody attachPart = service.users().messages().attachments()
                            .get("me", msg.getId(), attachmentId)
                            .execute();

                    byte[] fileBytes = Base64.getUrlDecoder().decode(attachPart.getData());

                    // ✅ Save file
                    String dirPath = System.getProperty("user.home") + "/emails/";
                    Files.createDirectories(Paths.get(dirPath));

                    String filePath = dirPath + fileName;

                    if (!Files.exists(Paths.get(filePath))) {
                        Files.write(Paths.get(filePath), fileBytes);
                    }

                    System.out.println("Downloaded: " + fileName);

                    String result = pdfProcessingService.processPdf(filePath, CryptoUtil.decrypt(panCard));

                    System.out.println("Processed Data: " + result);
                }
            }
        }
    }

    // ✅ TOKEN HANDLING
    private String getValidAccessToken(UserDetails userDetails) {
        String accessToken = CryptoUtil.decrypt(userDetails.getAccessToken());
        return accessToken; // future: expiry check
    }

    private String refreshAccessToken(String refreshToken) throws Exception {

        GoogleTokenResponse response =
                new GoogleRefreshTokenRequest(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        refreshToken,
                        CLIENT_ID,
                        CLIENT_SECRET
                ).execute();

        return response.getAccessToken();
    }

    // ✅ GMAIL CLIENT
    private Gmail getGmailService(String accessToken) throws Exception {

        Credential credential = new Credential.Builder(
                BearerToken.authorizationHeaderAccessMethod())
                .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                .setJsonFactory(GsonFactory.getDefaultInstance())
                .build()
                .setAccessToken(accessToken);

        return new Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential
        ).setApplicationName("TradeLedger").build();
    }
}
