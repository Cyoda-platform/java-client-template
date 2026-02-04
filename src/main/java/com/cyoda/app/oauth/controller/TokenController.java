package com.cyoda.app.oauth.controller;

import com.cyoda.app.oauth.dto.TokenRequest;
import com.cyoda.app.oauth.dto.TokenResponse;
import com.cyoda.app.oauth.entity.TechnicalUser;
import com.cyoda.app.oauth.exception.InvalidClientException;
import com.cyoda.app.oauth.exception.InvalidGrantException;
import com.cyoda.app.oauth.service.TechnicalUserService;
import com.cyoda.app.oauth.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.Set;

/**
 * OAuth2 Token Endpoint Controller
 * Implements RFC 6749 client credentials grant flow.
 * Endpoint: POST /api/v1/oauth/token
 */
@RestController
@RequestMapping("/v1/oauth/token")
@RequiredArgsConstructor
@Slf4j
public class TokenController {

    private final TechnicalUserService technicalUserService;
    private final TokenService tokenService;

    /**
     * Issue an access token using client credentials grant.
     * Accepts both form-encoded and JSON request bodies.
     *
     * @param request the token request containing client_id, client_secret, and optional scope
     * @return TokenResponse with access_token, token_type, and expires_in
     */
    @PostMapping
    public ResponseEntity<TokenResponse> issueToken(@RequestBody TokenRequest request) {
        log.info("Token request received for clientId: {}", request.getClientId());

        // Validate grant type
        if (request.getGrantType() == null || !request.getGrantType().equals("client_credentials")) {
            log.warn("Invalid grant type: {}", request.getGrantType());
            throw new InvalidGrantException("Unsupported grant_type. Only 'client_credentials' is supported.");
        }

        // Validate required fields
        if (request.getClientId() == null || request.getClientId().isBlank()) {
            throw new InvalidClientException("client_id is required");
        }
        if (request.getClientSecret() == null || request.getClientSecret().isBlank()) {
            throw new InvalidClientException("client_secret is required");
        }

        // Validate credentials
        Optional<TechnicalUser> technicalUser = technicalUserService.validateCredentials(
                request.getClientId(),
                request.getClientSecret()
        );

        if (technicalUser.isEmpty()) {
            log.warn("Invalid credentials for clientId: {}", request.getClientId());
            throw new InvalidClientException("Invalid client_id or client_secret");
        }

        TechnicalUser user = technicalUser.get();
        Set<String> scopes = user.getScopes();

        // Generate token
        String accessToken = tokenService.generateToken(request.getClientId(), scopes);
        long expiresIn = tokenService.getTokenExpirationSeconds();

        TokenResponse response = TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .scope(String.join(" ", scopes))
                .build();

        log.info("Token issued successfully for clientId: {}", request.getClientId());
        return ResponseEntity.ok(response);
    }

    /**
     * Exception handler for InvalidClientException
     */
    @ExceptionHandler(InvalidClientException.class)
    public ResponseEntity<ErrorResponse> handleInvalidClient(InvalidClientException ex) {
        log.error("Invalid client error: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("invalid_client", ex.getMessage()));
    }

    /**
     * Exception handler for InvalidGrantException
     */
    @ExceptionHandler(InvalidGrantException.class)
    public ResponseEntity<ErrorResponse> handleInvalidGrant(InvalidGrantException ex) {
        log.error("Invalid grant error: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("invalid_grant", ex.getMessage()));
    }

    /**
     * Error response DTO
     */
    public record ErrorResponse(String error, String errorDescription) {}
}

