package com.example.tradeLedger.serviceImpl;

import com.example.tradeLedger.dto.PanUpdateRequest;
import com.example.tradeLedger.entity.UserDetails;
import com.example.tradeLedger.repository.UserDetailsRepository;
import com.example.tradeLedger.service.UserService;
import com.example.tradeLedger.utils.CryptoUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserDetailsRepository userDetailsRepository;

    public UserServiceImpl(UserDetailsRepository userDetailsRepository) {
        this.userDetailsRepository = userDetailsRepository;
    }

    @Override
    public ResponseEntity<?> updatePan(PanUpdateRequest request, Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        String email = (String) authentication.getPrincipal();
        UserDetails user = userDetailsRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String pan = request.getPanCard();
        if (pan == null || !pan.toLowerCase().matches("[a-z]{5}[0-9]{4}[a-z]{1}")) {
            return ResponseEntity.badRequest().body("Invalid PAN format");
        }

        user.setPanCard(CryptoUtil.encrypt(pan.toLowerCase()));
        userDetailsRepository.save(user);

        return ResponseEntity.ok("PAN updated securely");
    }
}
