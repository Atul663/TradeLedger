package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.entity.User;
import com.example.tradeLedger.exception.ResourceConflictException;
import com.example.tradeLedger.repository.UserRepository;
import com.example.tradeLedger.service.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class CurrentUserServiceImpl implements CurrentUserService {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserServiceImpl.class);

    /**
     * users.password_hash is NOT NULL, but callers here authenticate through
     * Google - there is no local credential to store. This sentinel is not a
     * hash of anything and can never match a verification, which is the point:
     * it records "authentication happens elsewhere" without weakening anything.
     */
    private static final String EXTERNAL_AUTH_SENTINEL = "EXTERNAL_AUTH:GOOGLE";

    private static final int USERNAME_MAX = 50;

    private final UserRepository userRepository;

    public CurrentUserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public User require(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalized).orElseGet(() -> provision(normalized));

        if (!User.STATUS_ACTIVE.equals(user.getStatus())) {
            throw new ResourceConflictException("User account is " + user.getStatus());
        }
        return user;
    }

    private User provision(String email) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(uniqueUsername(email));
        user.setPasswordHash(EXTERNAL_AUTH_SENTINEL);
        user.setStatus(User.STATUS_ACTIVE);
        // users.email is UNIQUE, so two concurrent first-ever requests from the
        // same caller race here. The loser's insert violates the constraint and
        // that is left to propagate: catching it would not help, because the
        // failed flush has already marked the transaction rollback-only. The
        // caller sees a 409 and the retry finds the row the winner created.
        User saved = userRepository.save(user);
        log.info("Provisioned control-plane user {} for {}", saved.getId(), email);
        return saved;
    }

    /**
     * users.username is NOT NULL UNIQUE but the auth flow never supplies one, so
     * it is derived from the email local part and disambiguated on collision.
     */
    private String uniqueUsername(String email) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9._-]", "");
        if (base.isBlank()) {
            base = "user";
        }
        String candidate = truncate(base, USERNAME_MAX);
        for (int suffix = 2; userRepository.existsByUsername(candidate); suffix++) {
            String tail = "-" + suffix;
            candidate = truncate(base, USERNAME_MAX - tail.length()) + tail;
        }
        return candidate;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
