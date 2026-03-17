package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.service.GeminiService;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GeminiServiceImpl implements GeminiService {

    private static final String API_KEY = "AIzaSyBAKRGim8rvMg2vEb99KDoQ50HJ4xmgrwY";

    @Override
    public String extractData(String text) {

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=" + API_KEY;
        String prompt = """
            Extract the following details from the trading report:
            
            1. Obligation Details:
            - payInPayOut (PAY IN / PAY OUT OBLIGATION)
            - brokerage (Brokerage Charges)
            - netAmount (Net amount receivable / payable)
            
            2. Annexure A (Trades):
            Extract all rows as a list with:
            - contract (full contract name)
            - buySell (B or S)
            - quantity
            - wap (average price)
            - netTotal
            
            Rules:
            - Return ONLY valid JSON
            - Do NOT add explanation
            - Do NOT add extra text
            - Ensure all numbers are numeric (no commas, no currency symbols)
            
            Expected JSON format:
            
            {
              "obligation": {
                "payInPayOut": number,
                "brokerage": number,
                "netAmount": number
              },
              "annexure": [
                {
                  "contract": "",
                  "buySell": "",
                  "quantity": number,
                  "wap": number,
                  "netTotal": number
                }
              ]
            }
            
            Text:
            """ + text;

        String safePrompt = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");

        String body = """
        {
          "contents": [
            {
              "parts": [
                {"text": "%s"}
              ]
            }
          ]
        }
        """.formatted(safePrompt);


        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(body, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        return response.getBody();
    }
}