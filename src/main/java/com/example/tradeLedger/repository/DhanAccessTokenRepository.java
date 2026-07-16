package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.DhanAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DhanAccessTokenRepository extends JpaRepository<DhanAccessToken, Long> {
    Optional<DhanAccessToken> findFirstByOrderByUpdatedAtDesc();
}
