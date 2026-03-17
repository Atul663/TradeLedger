package com.example.tradeLedger.service;

public interface PdfService {

    String extractText(String filePath, String password) throws Exception;

    String extractInvoiceSection(String text);

    String extractObligationSection(String fullText);

    String extractAnnexureSection(String fullText);
}