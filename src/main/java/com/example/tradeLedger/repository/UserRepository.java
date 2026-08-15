package com.example.tradeLedger.repository;

import com.example.tradeLedger.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Control-plane {@code users}. Separate from {@link UserDetailsRepository}, which
 * backs the existing authentication flow and is not touched by this module.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);
}
