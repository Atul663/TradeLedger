package com.example.tradeLedger.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;

public interface GoogleAuthService {

    void googleLogin(String redirect, HttpServletResponse response) throws Exception;

    void callback(String code, String state, HttpServletResponse response) throws Exception;

    ResponseEntity<?> me(HttpServletRequest request);

    ResponseEntity<?> refresh(HttpServletRequest request);

    ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response);
}
