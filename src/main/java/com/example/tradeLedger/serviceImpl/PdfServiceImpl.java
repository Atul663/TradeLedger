package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.service.PdfService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class PdfServiceImpl implements PdfService {

    @Override
    public String extractText(String filePath, String password) throws Exception {

        PDDocument document = PDDocument.load(new File(filePath), password);

        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(document);

        document.close();

        return text;
    }

    @Override
    public String extractInvoiceSection(String text) {

        String lower = text.toLowerCase();

        int start = lower.indexOf("invoice");
        int end = lower.indexOf("total");

        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 200);
        }

        return text.substring(0, Math.min(text.length(), 5000));
    }

    public String extractObligationSection(String text) {

        String lower = text.toLowerCase();

        int start = lower.indexOf("obligation details");
        int end = lower.indexOf("gst");

        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end);
        }

        return text;
    }

    @Override
    public String extractAnnexureSection(String text) {

        try {
            // 🔥 Use regex to match ONLY "Annexure A"
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "Annexure A[\\s\\S]*",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );

            java.util.regex.Matcher matcher = pattern.matcher(text);

            if (matcher.find()) {
                String result = matcher.group();

                System.out.println("✅ Annexure extracted correctly");
                return result;
            } else {
                System.out.println("❌ Annexure A not found");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }
}