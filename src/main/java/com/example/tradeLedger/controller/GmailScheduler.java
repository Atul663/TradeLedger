package com.example.tradeLedger.controller;

import com.example.tradeLedger.service.GmailSchedulerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GmailScheduler {

    private final GmailSchedulerService gmailSchedulerService;

    public GmailScheduler(GmailSchedulerService gmailSchedulerService) {
        this.gmailSchedulerService = gmailSchedulerService;
    }

//    @Scheduled(fixedDelay = 300000)
    public void processEmails() {
        gmailSchedulerService.processEmails();
    }
}
