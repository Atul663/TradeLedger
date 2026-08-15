package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.UserRiskLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRiskLimitRepository extends JpaRepository<UserRiskLimit, UUID> {
}
