package com.java_template.application.controller;

import com.java_template.application.auth.AuthService;
import com.java_template.application.auth.JwtTokenProvider;
import com.java_template.application.auth.User;
import com.java_template.application.dto.LoginRequest;
import com.java_template.application.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller for TMS
 */
@RestController
@RequestMapping("/login")
@Tag(name = "Authentication", description = "Authentication endpoints")
public class AuthController {
    private final AuthService authService;
    private final JwtTokenProvider tokenProvider;

    public AuthController(AuthService authService, JwtTokenProvider tokenProvider) {
        this.authService = authService;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping
    @Operation(summary = "Login with username and password", description = "Authenticate user and return JWT token")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        var user = authService.authenticate(request.getUsername(), request.getPassword());

        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, request.getUsername(), null, null));
        }

        String token = tokenProvider.generateToken(request.getUsername());
        return ResponseEntity.ok(new LoginResponse(token, request.getUsername(), "TESTER", java.time.LocalDateTime.now().plusHours(1)));
    }
}

