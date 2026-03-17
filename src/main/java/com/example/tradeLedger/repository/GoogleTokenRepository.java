package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.GoogleToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoogleTokenRepository extends JpaRepository<GoogleToken, Long> {

    Optional<GoogleToken> findByEmail(String email);

    List<GoogleToken> findAll(); // for scheduler
}