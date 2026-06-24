package com.example.tradeLedger.security;

import com.example.tradeLedger.utils.JwtUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                String type = JwtUtil.extractType(token);
                if ("access".equals(type)) {
                    String email = JwtUtil.extractEmail(token);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.info("AUTH [{}] {} {}", email, request.getMethod(), request.getRequestURI());
                } else {
                    log.warn("401 UNAUTHORIZED - Wrong token type '{}' on {} {}", type, request.getMethod(), request.getRequestURI());
                    sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Wrong token type: " + type);
                    return;
                }
            } catch (Exception e) {
                log.warn("401 UNAUTHORIZED - Invalid/expired token on {} {} | reason: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}
