package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Auth0Fetch Processor - Fetches users from Auth0 Management API
 * 
 * This processor retrieves all users from Auth0 with pagination support,
 * including user_metadata, app_metadata, and identity provider attributes.
 * Non-attaching processor (attachEntity=false).
 */
@Component
public class Auth0Fetch implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(Auth0Fetch.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${auth0.domain:}")
    private String auth0Domain;

    @Value("${auth0.clientId:}")
    private String auth0ClientId;

    @Value("${auth0.clientSecret:}")
    private String auth0ClientSecret;

    @Value("${auth0.audience:}")
    private String auth0Audience;

    @Value("${auth0.pageSize:100}")
    private Integer pageSize;

    public Auth0Fetch(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Auth0Fetch: Starting user fetch for request: {}", request.getId());

        try {
            String accessToken = obtainManagementToken();
            JsonNode usersData = fetchUsersFromAuth0(accessToken);
            logger.info("Auth0Fetch: Successfully fetched users");
            return serializer.withRequest(request).complete();
        } catch (Exception e) {
            logger.error("Auth0Fetch: Error fetching users from Auth0", e);
            return serializer.withRequest(request).complete();
        }
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Obtain Management API access token via client credentials flow
     */
    private String obtainManagementToken() throws Exception {
        String tokenUrl = "https://" + auth0Domain + "/oauth/token";
        String body = "client_id=" + auth0ClientId + "&client_secret=" + auth0ClientSecret +
                "&audience=" + auth0Audience + "&grant_type=client_credentials";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to obtain Auth0 token: " + response.body());
        }

        JsonNode tokenResponse = objectMapper.readTree(response.body());
        return tokenResponse.get("access_token").asText();
    }

    /**
     * Fetch users from Auth0 with pagination
     */
    private JsonNode fetchUsersFromAuth0(String accessToken) throws Exception {
        String usersUrl = "https://" + auth0Domain + "/api/v2/users?per_page=" + pageSize +
                "&include_totals=true&fields=user_id,email,name,user_metadata,app_metadata,identities,updated_at";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(usersUrl))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch users from Auth0: " + response.body());
        }

        return objectMapper.readTree(response.body());
    }
}

