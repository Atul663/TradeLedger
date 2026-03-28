package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.UserDetails;
import com.example.tradeLedger.repository.UserDetailsRepository;
import com.example.tradeLedger.service.GoogleAuthService;
import com.example.tradeLedger.service.UserDetailsService;
import com.example.tradeLedger.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private final UserDetailsService userDetailsService;
    private final UserDetailsRepository userDetailsRepository;

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    private static final String GOOGLE_CALLBACK_URL = "https://trade-ledger-n6ps.onrender.com/api/v1/auth/callback";
//    private static final String GOOGLE_CALLBACK_URL = "http://localhost:8080/api/v1/auth/callback";
    private static final String DEFAULT_FRONTEND_BASE_URL =
            "https://trade-pnl-analysis.vercel.app";

    public GoogleAuthServiceImpl(UserDetailsService userDetailsService,
                                 UserDetailsRepository userDetailsRepository) {
        this.userDetailsService = userDetailsService;
        this.userDetailsRepository = userDetailsRepository;
    }

    @Override
    public void googleLogin(String redirect, HttpServletResponse response) throws Exception {
        String frontendBaseUrl = resolveRedirectBaseUrl(redirect);
        String encodedCallbackUrl = URLEncoder.encode(GOOGLE_CALLBACK_URL, StandardCharsets.UTF_8);
        String encodedScope = URLEncoder.encode(
                "https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/userinfo.email",
                StandardCharsets.UTF_8
        );
        String encodedState = URLEncoder.encode(frontendBaseUrl, StandardCharsets.UTF_8);

        String url = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=" + clientId +
                "&redirect_uri=" + encodedCallbackUrl +
                "&response_type=code" +
                "&scope=" + encodedScope +
                "&access_type=offline" +
                "&prompt=consent" +
                "&state=" + encodedState;

        response.sendRedirect(url);
    }

    @Override
    public void callback(String code, String state, HttpServletResponse response) throws Exception {
        GoogleTokenResponse tokenResponse =
                new GoogleAuthorizationCodeTokenRequest(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        "https://oauth2.googleapis.com/token",
                        clientId,
                        clientSecret,
                        code,
                        GOOGLE_CALLBACK_URL
                ).execute();

        String googleAccessToken = tokenResponse.getAccessToken();
        String googleRefreshToken = tokenResponse.getRefreshToken();
        String email = getUserEmail(googleAccessToken);

        userDetailsService.saveOrUpdateToken(email, googleAccessToken, googleRefreshToken);

        String jwtRefreshToken = JwtUtil.generateRefreshToken(email);
        Cookie cookie = buildRefreshCookie(jwtRefreshToken, 7 * 24 * 60 * 60);

        response.addCookie(cookie);
        response.sendRedirect(buildFrontendTradesUrl(state));
    }

    @Override
    public ResponseEntity<?> me(HttpServletRequest request) {
        String refreshToken = extractRefreshToken(request);

        if (refreshToken == null) {
            return ResponseEntity.status(401).body("Not authenticated");
        }

        try {
            String email = JwtUtil.extractEmail(refreshToken);
            UserDetails userDetails = userDetailsRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (userDetails.isRevoked()) {
                return ResponseEntity.status(401).body("Token revoked");
            }

            String accessToken = JwtUtil.generateAccessToken(email);
            return ResponseEntity.ok(Map.of(
                    "email", email,
                    "accessToken", accessToken,
                    "hasPanCard", hasPanCard(userDetails)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid token");
        }
    }

    @Override
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        String refreshToken = extractRefreshToken(request);

        if (refreshToken == null) {
            return ResponseEntity.status(401).body("Refresh token missing");
        }

        try {
            String email = JwtUtil.extractEmail(refreshToken);
            UserDetails userDetails = userDetailsRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (userDetails.isRevoked()) {
                return ResponseEntity.status(401).body("Token revoked");
            }

            return ResponseEntity.ok(Map.of(
                    "accessToken", JwtUtil.generateAccessToken(email)
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid refresh token");
        }
    }

    @Override
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshToken(request);

        if (refreshToken != null) {
            try {
                String email = JwtUtil.extractEmail(refreshToken);
                UserDetails userDetails = userDetailsRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("User not found"));
                userDetails.setRevoked(true);
                userDetailsRepository.save(userDetails);
            } catch (Exception ignored) {
            }
        }

        response.addCookie(buildRefreshCookie(null, 0));
        return ResponseEntity.ok("Logged out");
    }

    private Cookie buildRefreshCookie(String value, int maxAge) {
        Cookie cookie = new Cookie("refresh_token", value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        if (maxAge > 0) {
            cookie.setAttribute("SameSite", "None");
        }
        return cookie;
    }

    private String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

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

    private String buildFrontendTradesUrl(String state) {
        String baseUrl = DEFAULT_FRONTEND_BASE_URL;

        if (state != null && !state.isBlank()) {
            baseUrl = resolveRedirectBaseUrl(state);
        }

        if (baseUrl.endsWith("/trades")) {
            return baseUrl;
        }

        if (baseUrl.endsWith("/")) {
            return baseUrl + "trades";
        }

        return baseUrl + "/trades";
    }

    private String resolveRedirectBaseUrl(String redirect) {
        if (redirect == null || redirect.isBlank()) {
            return DEFAULT_FRONTEND_BASE_URL;
        }

        String normalizedRedirect = redirect.trim();

        try {
            URI uri = URI.create(normalizedRedirect);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return DEFAULT_FRONTEND_BASE_URL;
            }

            return stripTrailingSlash(stripTradesPath(normalizedRedirect));
        } catch (IllegalArgumentException exception) {
            return DEFAULT_FRONTEND_BASE_URL;
        }
    }

    private String stripTradesPath(String url) {
        if (url.endsWith("/trades")) {
            return url.substring(0, url.length() - "/trades".length());
        }

        return stripTrailingSlash(url);
    }

    private String stripTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }

        return url;
    }

    private boolean hasPanCard(UserDetails userDetails) {
        return userDetails.getPanCard() != null && !userDetails.getPanCard().isBlank();
    }
}
