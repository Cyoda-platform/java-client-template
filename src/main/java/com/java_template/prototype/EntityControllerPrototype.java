```java
package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/deploy")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simple in-memory job status store for prototype validation
    private final Map<String, JobStatus> cyodaEnvJobs = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> userAppJobs = new ConcurrentHashMap<>();

    private static final String TEAMCITY_BASE_URL = "https://teamcity.test/app/rest";

    // ------------ DTOs ------------

    @Data
    public static class DeployCyodaEnvRequest {
        @NotBlank
        private String user_name;
    }

    @Data
    public static class DeployUserAppRequest {
        @NotBlank
        private String repository_url;
        @NotBlank
        private String user_name;
        private boolean is_public; // boolean, not string
    }

    @Data
    public static class CancelBuildRequest {
        @NotBlank
        private String comment;
        private boolean readdIntoQueue;
    }

    @Getter
    @Setter
    public static class JobStatus {
        private String status;
        private Instant requestedAt;

        public JobStatus(String status, Instant requestedAt) {
            this.status = status;
            this.requestedAt = requestedAt;
        }
    }

    // ------------ Utils ------------

    private HttpHeaders buildAuthHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(MediaType.APPLICATION_JSON.asList());
        return headers;
    }

    // ------------ Endpoints ------------

    /**
     * POST /prototype/deploy/cyoda-env
     * Triggers Cyoda Environment deployment build in TeamCity.
     */
    @PostMapping(path = "/cyoda-env", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> deployCyodaEnv(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody DeployCyodaEnvRequest request) {
        logger.info("Received deploy cyoda-env request for user: {}", request.getUser_name());

        String token = extractToken(authorization);

        // Prepare TeamCity request payload
        String teamCityUrl = TEAMCITY_BASE_URL + "/buildQueue";

        String bodyJson = String.format("""
            {
              "buildType": {
                "id": "KubernetesPipeline_CyodaSaas"
              },
              "properties": {
                "property": [
                  {
                    "name": "user_defined_keyspace",
                    "value": "%s"
                  },
                  {
                    "name": "user_defined_namespace",
                    "value": "%s"
                  }
                ]
              }
            }
            """, request.getUser_name(), request.getUser_name());

        HttpEntity<String> entity = new HttpEntity<>(bodyJson, buildAuthHeaders(token));

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(teamCityUrl, entity, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            String buildId = jsonNode.path("id").asText();

            if (buildId == null || buildId.isEmpty()) {
                logger.error("TeamCity response missing build id: {}", response.getBody());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No build id returned from TeamCity");
            }

            // Store job status as "processing" with timestamp
            cyodaEnvJobs.put(buildId, new JobStatus("processing", Instant.now()));

            logger.info("Triggered Cyoda env deployment, buildId: {}", buildId);
            return Map.of("build_id", buildId);

        } catch (Exception e) {
            logger.error("Error triggering Cyoda env deployment", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to trigger deployment: " + e.getMessage());
        }
    }

    /**
     * POST /prototype/deploy/user-app
     * Triggers User Application deployment build in TeamCity.
     */
    @PostMapping(path = "/user-app", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> deployUserApp(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody DeployUserAppRequest request) {
        logger.info("Received deploy user-app request for user: {}, repo: {}", request.getUser_name(), request.getRepository_url());

        String token = extractToken(authorization);

        String teamCityUrl = TEAMCITY_BASE_URL + "/buildQueue";

        String bodyJson = String.format("""
            {
              "buildType": {
                "id": "KubernetesPipeline_CyodaSaasUserEnv"
              },
              "properties": {
                "property": [
                  {
                    "name": "repository_url",
                    "value": "%s"
                  },
                  {
                    "name": "user_defined_namespace",
                    "value": "%s"
                  }
                ]
              }
            }
            """, request.getRepository_url(), request.getUser_name());

        HttpEntity<String> entity = new HttpEntity<>(bodyJson, buildAuthHeaders(token));

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(teamCityUrl, entity, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            String buildId = jsonNode.path("id").asText();

            if (buildId == null || buildId.isEmpty()) {
                logger.error("TeamCity response missing build id: {}", response.getBody());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No build id returned from TeamCity");
            }

            // Store job status as "processing" with timestamp
            userAppJobs.put(buildId, new JobStatus("processing", Instant.now()));

            logger.info("Triggered User app deployment, buildId: {}", buildId);
            return Map.of("build_id", buildId);

        } catch (Exception e) {
            logger.error("Error triggering User app deployment", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to trigger deployment: " + e.getMessage());
        }
    }

    /**
     * GET /prototype/deploy/cyoda-env/status/{build_id}
     * Retrieves Cyoda environment deployment status from TeamCity.
     */
    @GetMapping(path = "/cyoda-env/status/{build_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getCyodaEnvStatus(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("build_id") String buildId) {
        logger.info("Fetching Cyoda env status for buildId: {}", buildId);

        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/buildQueue/id:" + buildId;

        return fetchExternalJson(url, token);
    }

    /**
     * GET /prototype/deploy/cyoda-env/statistics/{build_id}
     * Retrieves Cyoda environment deployment statistics from TeamCity.
     */
    @GetMapping(path = "/cyoda-env/statistics/{build_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getCyodaEnvStatistics(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("build_id") String buildId) {
        logger.info("Fetching Cyoda env statistics for buildId: {}", buildId);

        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/builds/id:" + buildId + "/statistics/";

        return fetchExternalJson(url, token);
    }

    /**
     * GET /prototype/deploy/user-app/status/{build_id}
     * Retrieves User application deployment status from TeamCity.
     */
    @GetMapping(path = "/user-app/status/{build_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getUserAppStatus(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("build_id") String buildId) {
        logger.info("Fetching User app status for buildId: {}", buildId);

        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/buildQueue/id:" + buildId;

        return fetchExternalJson(url, token);
    }

    /**
     * GET /prototype/deploy/user-app/statistics/{build_id}
     * Retrieves User application deployment statistics from TeamCity.
     */
    @GetMapping(path = "/user-app/statistics/{build_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getUserAppStatistics(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("build_id") String buildId) {
        logger.info("Fetching User app statistics for buildId: {}", buildId);

        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/builds/id:" + buildId + "/statistics/";

        return fetchExternalJson(url, token);
    }

    /**
     * POST /prototype/deploy/cancel/user-app/{build_id}
     * Cancel a queued User app deployment build in TeamCity.
     */
    @PostMapping(path = "/cancel/user-app/{build_id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> cancelUserAppBuild(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("build_id") String buildId,
            @Valid @RequestBody CancelBuildRequest request) {
        logger.info("Cancel request for User app buildId: {}, comment: {}", buildId, request.getComment());

        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/builds/id:" + buildId;

        String bodyJson = String.format("""
            {
              "comment": "%s",
              "readdIntoQueue": %s
            }
            """, request.getComment(), request.isReaddIntoQueue());

        HttpEntity<String> entity = new HttpEntity<>(bodyJson, buildAuthHeaders(token));

        try {
            restTemplate.postForEntity(url, entity, String.class);

            // Update in-memory job status if exists
            if (userAppJobs.containsKey(buildId)) {
                userAppJobs.put(buildId, new JobStatus("canceled", Instant.now()));
            }

            logger.info("Cancelled User app buildId: {}", buildId);
            return Map.of("result", "success");

        } catch (Exception e) {
            logger.error("Error cancelling User app build", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to cancel build: " + e.getMessage());
        }
    }

    // ------------ Helpers ------------

    private JsonNode fetchExternalJson(String url, String token) {
        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(token));
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            logger.error("Error fetching external JSON from {}", url, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch external data: " + e.getMessage());
        }
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            logger.error("Missing or invalid Authorization header");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing Authorization header");
        }
        return authorizationHeader.substring(7);
    }

    // ------------ Minimal Error Handling ------------

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        HttpStatus status = ex.getStatusCode();
        return Map.of(
                "error", status.toString(),
                "message", ex.getReason() != null ? ex.getReason() : ex.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleGenericException(Exception ex) {
        logger.error("Unhandled exception", ex);
        return Map.of(
                "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "message", "An unexpected error occurred"
        );
    }
}
```