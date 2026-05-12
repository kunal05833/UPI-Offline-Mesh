package com.upi.mesh.controller;

import com.upi.mesh.service.JwtService;
import com.upi.mesh.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private AuthenticationManager authManager;
    @Autowired private JwtService jwt;
    @Autowired private RateLimitService rateLimit;

    /**
     * POST /api/auth/login
     * Body: { "username": "admin", "password": "admin123" }
     * Returns: { "token": "...", "role": "ROLE_ADMIN" }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   HttpServletRequest httpReq) {
        // Rate limit by IP
        String ip = httpReq.getRemoteAddr();
        rateLimit.checkAuthLimit(ip);

        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));

            String role = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .findFirst().orElse("ROLE_BRIDGE");

            String token = jwt.generate(req.username(), role);

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "username", req.username(),
                    "role", role,
                    "expiresIn", "24h"
            ));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "INVALID_CREDENTIALS",
                    "message", "Username or password incorrect"
            ));
        }
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}
}
