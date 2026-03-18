package com.example.tradeLedger.controller;


import com.example.tradeLedger.serviceImpl.GmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/gmail")
public class GmailController {

    private final GmailService gmailService;

    public GmailController(GmailService gmailService) {
        this.gmailService = gmailService;
    }

    @GetMapping("/read-pdf")
    public String readPdf(Authentication authentication,
                          @RequestParam String email) throws Exception {

        String userEmail = (String) authentication.getPrincipal();

        gmailService.readEmailsWithAttachments(userEmail, email);

        return "PDFs downloaded successfully!";
    }
}