package com.example.tradeLedger.service;

import com.example.tradeLedger.dto.PanUpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

public interface UserService {

    ResponseEntity<?> updatePan(PanUpdateRequest request, Authentication authentication);
}
