package com.cyoda.app.oauth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2 Token Request DTO
 * Supports both form-encoded and JSON request bodies.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenRequest {

    @JsonProperty("grant_type")
    private String grantType;

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("client_secret")
    private String clientSecret;

    @JsonProperty("scope")
    private String scope;
}

