package com.java_template.application.controller;

import com.java_template.application.auth.AuthService;
import com.java_template.application.dto.LoginRequest;
import com.java_template.application.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * REST controller for Authentication operations
 */
@RestController
@RequestMapping("/login")
@Tag(name = "Authentication", description = "Authentication endpoints")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    @Operation(summary = "Login with username and password", description = "Authenticate user and return JWT token")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResponse authResponse = authService.authenticate(request.getUsername(), request.getPassword());

        if (authResponse == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, request.getUsername(), null, null));
        }

        LocalDateTime expiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(authResponse.expiresAt),
                ZoneId.systemDefault()
        );

        return ResponseEntity.ok(new LoginResponse(
                authResponse.token,
                authResponse.username,
                authResponse.role,
                expiresAt
        ));
    }
}

