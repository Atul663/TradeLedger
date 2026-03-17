package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.service.GeminiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GeminiServiceImpl implements GeminiService {
    @Value("${gemini.api.key}")
    private String API_KEY;

    @Override
    public String extractData(String text) {

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + API_KEY;

        String prompt =
                "Extract the following details from the trading report:\n\n" +

                        "1. Obligation Details:\n" +
                        "- payInPayOut (PAY IN / PAY OUT OBLIGATION)\n" +
                        "- brokerage (Brokerage Charges)\n" +
                        "- netAmount (Net amount receivable / payable)\n\n" +

                        "2. Annexure A (Trades):\n" +
                        "Extract all rows as a list with:\n" +
                        "- contract (full contract name)\n" +
                        "- buySell (B or S)\n" +
                        "- quantity\n" +
                        "- wap (average price)\n" +
                        "- netTotal\n\n" +

                        "Rules:\n" +
                        "- Return ONLY valid JSON\n" +
                        "- Do NOT add explanation\n" +
                        "- Do NOT add extra text\n" +
                        "- Ensure all numbers are numeric (no commas, no currency symbols)\n\n" +

                        "Expected JSON format:\n\n" +

                        "{\n" +
                        "  \"obligation\": {\n" +
                        "    \"payInPayOut\": number,\n" +
                        "    \"brokerage\": number,\n" +
                        "    \"netAmount\": number\n" +
                        "  },\n" +
                        "  \"annexure\": [\n" +
                        "    {\n" +
                        "      \"contract\": \"\",\n" +
                        "      \"buySell\": \"\",\n" +
                        "      \"quantity\": number,\n" +
                        "      \"wap\": number,\n" +
                        "      \"netTotal\": number\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}\n\n" +

                        "Text:\n" +
                        text;

        // ✅ Simple JSON body (no risky formatting)
        String body = "{\n" +
                "  \"contents\": [\n" +
                "    {\n" +
                "      \"parts\": [\n" +
                "        {\"text\": " + toJsonString(prompt) + "}\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response =
                restTemplate.postForEntity(url, request, String.class);

        return response.getBody();
    }

    // ✅ Safe JSON string escape
    private String toJsonString(String input) {
        return "\"" + input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }
}