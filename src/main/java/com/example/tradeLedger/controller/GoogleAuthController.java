package com.example.tradeLedger.controller;

import com.example.tradeLedger.serviceImpl.GoogleTokenService;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
import com.example.tradeLedger.entity.GoogleToken;

import java.util.Map;

@RestController
public class GoogleAuthController {

    @Value("${google.client.id}")
    private String CLIENT_ID;

    @Value("${google.client.secret}")
    private String CLIENT_SECRET;

    private final GoogleTokenService tokenService;

    private final String REDIRECT_URL = "https://trade-ledger-n6ps.onrender.com/oauth2/callback";
//    private final String REDIRECT_URL = "https://localhost:8080/oauth2/callback";

    public GoogleAuthController(GoogleTokenService tokenService) {
        this.tokenService = tokenService;
    }

    // 1️⃣ Login API
    @GetMapping("/auth/google")
    public void googleLogin(HttpServletResponse response) throws Exception {

        String url = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=" + CLIENT_ID +
                "&redirect_uri="+REDIRECT_URL +
                "&response_type=code" +
                "&scope=https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/userinfo.email" +
                "&access_type=offline" +
                "&prompt=consent";

        response.sendRedirect(url);
    }

    // 2️⃣ Callback API
    @GetMapping("/oauth2/callback")
    public void callback(@RequestParam("code") String code, HttpServletResponse response) throws Exception {

        GoogleTokenResponse tokenResponse =
                new GoogleAuthorizationCodeTokenRequest(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        "https://oauth2.googleapis.com/token",
                        CLIENT_ID,
                        CLIENT_SECRET,
                        code,
                        REDIRECT_URL
                ).execute();

        String accessToken = tokenResponse.getAccessToken();
        String refreshToken = tokenResponse.getRefreshToken();

        // ✅ Save in DB
        String email = getUserEmail(accessToken);

        // ✅ Save per user
        GoogleToken tokenDetails = tokenService.saveOrUpdateToken(email, accessToken, refreshToken);

        // ✅ Redirect to dashboard after successful login
        response.sendRedirect("https://trade-pnl-analysis.vercel.app/trades");
    }

    private String getUserEmail(String accessToken) throws Exception {

        java.net.URL url = new java.net.URL("https://www.googleapis.com/oauth2/v2/userinfo");

        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);

        int status = conn.getResponseCode();

        java.io.InputStream stream;

        if (status == 200) {
            stream = conn.getInputStream();
        } else {
            // 🔥 VERY IMPORTANT (read error response)
            stream = conn.getErrorStream();
        }

        java.io.BufferedReader reader =
                new java.io.BufferedReader(new java.io.InputStreamReader(stream));

        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        System.out.println("Google Response: " + response);

        if (status != 200) {
            throw new RuntimeException("Failed to fetch user info: " + response);
        }

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(response.toString(), Map.class);

        return map.get("email").toString();
    }
}