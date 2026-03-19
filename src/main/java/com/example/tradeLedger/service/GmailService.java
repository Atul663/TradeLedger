package com.example.tradeLedger.service;

public interface GmailService {

    void readEmailsWithAttachments(String userEmail, String senderEmail) throws Exception;
}
