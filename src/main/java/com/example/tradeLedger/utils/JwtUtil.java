package com.example.tradeLedger.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;
public class JwtUtil {

    // Checked rather than dereferenced: an unset variable would otherwise
    // surface as an ExceptionInInitializerError from a static initialiser on the
    // first request, which says nothing about the missing configuration.
    private static final String SECRET = requireEnv("JWT_SECRET");
    private static final Key key = Keys.hmacShaKeyFor(SECRET.getBytes());

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " is not set. Add it to the service environment - at least 32 bytes, "
                            + "e.g. openssl rand -base64 32");
        }
        return value;
    }

    private static final long ACCESS_EXP = 1000L * 60 * 60 * 24; // 1 day
    private static final long REFRESH_EXP = 1000L * 60 * 60 * 24 * 7; // 7 days

    public static String generateAccessToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_EXP))
                .claim("type", "access")
                .signWith(key)
                .compact();
    }

    public static String generateRefreshToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_EXP))
                .claim("type", "refresh")
                .signWith(key)
                .compact();
    }

    public static String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    public static String extractType(String token) {
        return (String) getClaims(token).get("type");
    }

    private static Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}