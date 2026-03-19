package com.example.tradeLedger.controller;

import com.example.tradeLedger.service.PdfProcessingService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pdf")
public class PdfController {

    private final PdfProcessingService pdfProcessingService;

    public PdfController(PdfProcessingService pdfProcessingService) {
        this.pdfProcessingService = pdfProcessingService;
    }

    @GetMapping("/process")
    public String processPdf(
            @RequestParam String path,
            @RequestParam String password
    ) throws Exception {

        return pdfProcessingService.processPdf(path, password);
    }
}
