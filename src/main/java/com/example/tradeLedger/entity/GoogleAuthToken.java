package com.example.tradeLedger.entity;

import jakarta.persistence.*;

/**
 * Table {@code google_auth_tokens}: the Google OAuth session store for the login
 * flow - access/refresh token per email, and the revoked flag that ends it.
 *
 * NOT a user profile table, and NOT the control-plane {@link User}: those are
 * different rows in a different table with no foreign key between them. The only
 * link is the email string, which is also the JWT subject.
 */
@Entity
@Table(name = "google_auth_tokens")
public class GoogleAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @Column(columnDefinition = "TEXT")
    private String accessToken;

    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    private String panCard;

    private boolean revoked = false;

    private long createdAt;

    public GoogleAuthToken() {
    }

    public GoogleAuthToken(Long id, String email, String accessToken, String refreshToken, boolean revoked, long createdAt) {
        this.id = id;
        this.email = email;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.revoked = revoked;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getPanCard() {
        return panCard;
    }

    public void setPanCard(String panCard) {
        this.panCard = panCard;
    }
}
