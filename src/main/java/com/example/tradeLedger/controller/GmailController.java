package com.example.tradeLedger.controller;


import com.example.tradeLedger.serviceImpl.GmailService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/gmail")
public class GmailController {

    private final GmailService gmailService;

    public GmailController(GmailService gmailService) {
        this.gmailService = gmailService;
    }

    @GetMapping("/read")
    public List<String> readEmails(@RequestParam String accessToken) throws Exception {
        return gmailService.readEmails(accessToken);
    }

    @GetMapping("/read-pdf")
    public String readPdf(@RequestParam String accessToken, @RequestParam String email) throws Exception {

        gmailService.readEmailsWithAttachments(accessToken, email);

        return "PDFs downloaded successfully!";
    }
}