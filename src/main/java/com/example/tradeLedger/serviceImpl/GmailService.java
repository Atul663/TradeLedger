package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.service.PdfProcessingService;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class GmailService {

    @Autowired
    PdfProcessingService pdfProcessingService;
    public Gmail getGmailService(String accessToken) throws Exception {

        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
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

    public List<String> readEmails(String accessToken) throws Exception {

        Gmail service = getGmailService(accessToken);

        ListMessagesResponse response = service.users().messages().list("me")
                .setQ("atulprogramming2001@gmail.com") // 🔥 filter by domain
                .execute();

        List<String> emailSnippets = new ArrayList<>();

        if (response.getMessages() == null) {
            return emailSnippets;
        }

        for (Message msg : response.getMessages()) {

            Message fullMessage = service.users().messages()
                    .get("me", msg.getId())
                    .execute();

            emailSnippets.add(fullMessage.getSnippet());
        }

        return emailSnippets;
    }

    public void readEmailsWithAttachments(String accessToken, String email) throws Exception {

        Gmail service = getGmailService(accessToken);

        ListMessagesResponse response = service.users().messages().list("me")
                .setQ("from:" + email +" has:attachment filename:pdf")
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

                    // 🔥 SAVE TO DESKTOP
                    String dirPath = "/Users/atulkumarsethi/Desktop/emails/";
                    Files.createDirectories(Paths.get(dirPath));

                    String filePath = dirPath + fileName;

                    if (!Files.exists(Paths.get(filePath))) {
                        Files.write(Paths.get(filePath), fileBytes);
                    }

                    System.out.println("Downloaded: " + fileName);

                    // 🔥 PROCESS PDF
                    String password = "bjupn5708c";

                    String result = pdfProcessingService.processPdf(filePath, password);

                    System.out.println("Processed Data: " + result);
                }
            }
        }
    }
}