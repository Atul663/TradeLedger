package com.example.tradeLedger.service;

public interface PdfProcessingService {

    String processPdf(String filePath, String password) throws Exception;
}