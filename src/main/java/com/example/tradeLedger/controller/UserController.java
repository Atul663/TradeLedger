package com.example.tradeLedger.controller;

import com.example.tradeLedger.dto.PanUpdateRequest;
import com.example.tradeLedger.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/update-pan")
    public ResponseEntity<?> updatePan(@RequestBody PanUpdateRequest request,
                                       Authentication authentication) {
        return userService.updatePan(request, authentication);
    }
}
