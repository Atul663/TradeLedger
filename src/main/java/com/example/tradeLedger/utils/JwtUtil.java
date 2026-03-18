package com.example.tradeLedger.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;
public class JwtUtil {

    private static final String SECRET = System.getenv("JWT_SECRET");
    private static final Key key = Keys.hmacShaKeyFor(SECRET.getBytes());

    private static final long ACCESS_EXP = 1000 * 60 * 15; // 15 min
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