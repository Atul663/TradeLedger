package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.config.FrontendOrigins;
import com.example.tradeLedger.entity.GoogleAuthToken;
import com.example.tradeLedger.repository.GoogleAuthTokenRepository;
import com.example.tradeLedger.service.GoogleAuthService;
import com.example.tradeLedger.service.GoogleAuthTokenService;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class GoogleAuthServiceImpl implements GoogleAuthService {

    private final GoogleAuthTokenService googleAuthTokenService;
    private final GoogleAuthTokenRepository googleAuthTokenRepository;
    private final FrontendOrigins frontendOrigins;

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    /**
     * Where Google sends the browser back.
     *
     * Must match one of the "Authorized redirect URIs" on the OAuth client
     * byte-for-byte, and is sent twice - once to start the flow, once to redeem
     * the code - so both uses read this one field. On Render it defaults to the
     * external URL of the service, which the platform injects, so a first deploy
     * only needs that value pasting into the Google console.
     */
    private final String googleCallbackUrl;

    /** Where a login lands when the caller asked for nowhere in particular. */
    private final String defaultFrontendBaseUrl;

    public GoogleAuthServiceImpl(GoogleAuthTokenService googleAuthTokenService,
                                 GoogleAuthTokenRepository googleAuthTokenRepository,
                                 FrontendOrigins frontendOrigins,
                                 @Value("${app.oauth.google.callback-url}") String googleCallbackUrl,
                                 @Value("${app.frontend.base-url}") String defaultFrontendBaseUrl) {
        this.googleAuthTokenService = googleAuthTokenService;
        this.googleAuthTokenRepository = googleAuthTokenRepository;
        this.frontendOrigins = frontendOrigins;
        this.googleCallbackUrl = googleCallbackUrl.trim();
        this.defaultFrontendBaseUrl = stripTrailingSlash(defaultFrontendBaseUrl.trim());
    }

    @Override
    public void googleLogin(String redirect, HttpServletResponse response) throws Exception {
        String frontendBaseUrl = resolveRedirectBaseUrl(redirect);
        String encodedCallbackUrl = URLEncoder.encode(googleCallbackUrl, StandardCharsets.UTF_8);
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
                        googleCallbackUrl
                ).execute();

        String googleAccessToken = tokenResponse.getAccessToken();
        String googleRefreshToken = tokenResponse.getRefreshToken();
        String email = getUserEmail(googleAccessToken);

        googleAuthTokenService.saveOrUpdateToken(email, googleAccessToken, googleRefreshToken);

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
            GoogleAuthToken userDetails = googleAuthTokenRepository.findByEmail(email)
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
            GoogleAuthToken userDetails = googleAuthTokenRepository.findByEmail(email)
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
                GoogleAuthToken userDetails = googleAuthTokenRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("User not found"));
                userDetails.setRevoked(true);
                googleAuthTokenRepository.save(userDetails);
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
        // The UI is on a different site to the API - Vercel and Render are not
        // even the same registrable domain - so the browser drops this cookie on
        // any weaker pairing than SameSite=None with Secure.
        cookie.setAttribute("SameSite", "None");
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
        String baseUrl = defaultFrontendBaseUrl;

        if (state != null && !state.isBlank()) {
            baseUrl = resolveRedirectBaseUrl(state);
        }

        if (baseUrl.endsWith("/create-plan")) {
            return baseUrl;
        }

        if (baseUrl.endsWith("/")) {
            return baseUrl + "create-plan";
        }

        return baseUrl + "/create-plan";
    }

    /**
     * Honours the caller-supplied {@code ?redirect=} only when its origin is on
     * the configured allow-list, and falls back to the default otherwise.
     *
     * The value travels out through Google as {@code state} and comes back
     * attacker-controllable, so an unchecked one would forward a browser that has
     * just been issued a session cookie to any host on the internet.
     */
    private String resolveRedirectBaseUrl(String redirect) {
        if (redirect == null || redirect.isBlank() || !frontendOrigins.allows(redirect)) {
            return defaultFrontendBaseUrl;
        }

        return stripTrailingSlash(stripTradesPath(redirect.trim()));
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

    private boolean hasPanCard(GoogleAuthToken userDetails) {
        return userDetails.getPanCard() != null && !userDetails.getPanCard().isBlank();
    }
}
