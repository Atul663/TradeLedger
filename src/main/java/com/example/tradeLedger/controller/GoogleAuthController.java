package com.example.tradeLedger.controller;

import com.example.tradeLedger.repository.GoogleTokenRepository;
import com.example.tradeLedger.serviceImpl.GoogleTokenService;
import com.example.tradeLedger.utils.JwtUtil;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
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
    private final GoogleTokenRepository tokenRepository;

//    private final String REDIRECT_URL = "http://localhost:8080/oauth2/callback";
    private final String REDIRECT_URL = "https://trade-ledger-n6ps.onrender.com/oauth2/callback";


    public GoogleAuthController(GoogleTokenService tokenService,
                                GoogleTokenRepository tokenRepository) {
        this.tokenService = tokenService;
        this.tokenRepository = tokenRepository;
    }

    // ✅ LOGIN
    @GetMapping("/auth/google")
    public void googleLogin(@RequestParam("redirect") String redirect,
                            HttpServletResponse response) throws Exception {

        String url = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=" + CLIENT_ID +
                "&redirect_uri=" + REDIRECT_URL +
                "&response_type=code" +
                "&scope=https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/userinfo.email" +
                "&access_type=offline" +
                "&prompt=consent" +
                "&state=" + redirect;   // 👈 pass frontend URL safely

        response.sendRedirect(url);
    }

    // ✅ CALLBACK
    @GetMapping("/oauth2/callback")
    public void callback(@RequestParam("code") String code,
                         @RequestParam("state") String redirectUrl,
                         HttpServletResponse response) throws Exception {

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

        String googleAccessToken = tokenResponse.getAccessToken();
        String googleRefreshToken = tokenResponse.getRefreshToken();

        String email = getUserEmail(googleAccessToken);

        // ✅ Save encrypted Google tokens
        tokenService.saveOrUpdateToken(email, googleAccessToken, googleRefreshToken);

        // ✅ Generate JWT
        String jwtAccessToken = JwtUtil.generateAccessToken(email);
        String jwtRefreshToken = JwtUtil.generateRefreshToken(email);

        // 🔐 Store refresh token in cookie
        Cookie cookie = new Cookie("refresh_token", jwtRefreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // false for localhost
        cookie.setPath("/");
        cookie.setMaxAge(7 * 24 * 60 * 60);
        cookie.setAttribute("SameSite", "None");

        response.addCookie(cookie);

        // ✅ IMPORTANT FIX → NO TOKEN IN URL
        response.sendRedirect(redirectUrl + "/trades");
    }

    // ✅ GET CURRENT USER + ACCESS TOKEN
    @GetMapping("/auth/me")
    public ResponseEntity<?> me(HttpServletRequest request) {

        String refreshToken = extractRefreshToken(request);

        if (refreshToken == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }

        try {
            String email = JwtUtil.extractEmail(refreshToken);

            GoogleToken token = tokenRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (token.isRevoked()) {
                return ResponseEntity.status(401).body("Token revoked");
            }

            String accessToken = JwtUtil.generateAccessToken(email);

            return ResponseEntity.ok(Map.of(
                    "email", email,
                    "accessToken", accessToken
            ));

        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid token");
        }
    }

    // ✅ REFRESH
    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {

        String refreshToken = extractRefreshToken(request);

        if (refreshToken == null) {
            return ResponseEntity.status(401).body("Refresh token missing");
        }

        try {
            String email = JwtUtil.extractEmail(refreshToken);

            GoogleToken token = tokenRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (token.isRevoked()) {
                return ResponseEntity.status(401).body("Token revoked");
            }

            String newAccessToken = JwtUtil.generateAccessToken(email);

            return ResponseEntity.ok(Map.of(
                    "accessToken", newAccessToken
            ));

        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid refresh token");
        }
    }

    // ✅ LOGOUT
    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(HttpServletRequest request,
                                    HttpServletResponse response) {

        String refreshToken = extractRefreshToken(request);

        if (refreshToken != null) {
            try {
                String email = JwtUtil.extractEmail(refreshToken);

                GoogleToken token = tokenRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("User not found"));

                token.setRevoked(true);
                tokenRepository.save(token);

            } catch (Exception ignored) {}
        }

        Cookie cookie = new Cookie("refresh_token", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);

        response.addCookie(cookie);

        return ResponseEntity.ok("Logged out");
    }

    // ✅ HELPER
    private String extractRefreshToken(HttpServletRequest request) {

        if (request.getCookies() == null) return null;

        for (Cookie cookie : request.getCookies()) {
            if ("refresh_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private String getUserEmail(String accessToken) throws Exception {

        java.net.URL url = new java.net.URL("https://www.googleapis.com/oauth2/v2/userinfo");

        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);

        java.io.BufferedReader reader =
                new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));

        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(response.toString(), Map.class);

        return map.get("email").toString();
    }
}