package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.RiskProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RiskProfileRepository extends JpaRepository<RiskProfile, UUID> {

    List<RiskProfile> findAllByOrderByNameAsc();
}
