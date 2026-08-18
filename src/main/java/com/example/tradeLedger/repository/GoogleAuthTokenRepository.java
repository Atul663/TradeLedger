package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.GoogleAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoogleAuthTokenRepository extends JpaRepository<GoogleAuthToken, Long> {

    Optional<GoogleAuthToken> findByEmail(String email);
}
