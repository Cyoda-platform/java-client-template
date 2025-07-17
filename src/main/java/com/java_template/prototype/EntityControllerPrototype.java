package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/deploy")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, JobStatus> cyodaEnvJobs = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> userAppJobs = new ConcurrentHashMap<>();
    private static final String TEAMCITY_BASE_URL = "https://teamcity.test/app/rest";

    @Data
    public static class DeployCyodaEnvRequest {
        @NotBlank
        @Size(min = 1, max = 50)
        private String user_name;
    }

    @Data
    public static class DeployUserAppRequest {
        @NotBlank
        @Pattern(regexp = "https?://.+", message = "must be a valid URL")
        private String repository_url;
        private boolean is_public;
        @NotBlank
        @Size(min = 1, max = 50)
        private String user_name;
    }

    @Data
    public static class CancelBuildRequest {
        @NotBlank
        private String comment;
        private boolean readdIntoQueue;
    }

    @Getter @Setter
    public static class JobStatus {
        private String status;
        private Instant requestedAt;
        public JobStatus(String status, Instant requestedAt) {
            this.status = status;
            this.requestedAt = requestedAt;
        }
    }

    private String extractToken(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            logger.error("Invalid Authorization header");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing Authorization header");
        }
        return auth.substring(7);
    }

    private HttpHeaders buildAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(MediaType.APPLICATION_JSON.asList());
        return headers;
    }

    @PostMapping(path = "/cyoda-env", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> deployCyodaEnv(@RequestHeader("Authorization") String authorization,
                                              @RequestBody @Valid DeployCyodaEnvRequest request) {
        logger.info("deployCyodaEnv for user {}", request.getUser_name());
        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/buildQueue";
        String payload = String.format("""
            {
              "buildType":{"id":"KubernetesPipeline_CyodaSaas"},
              "properties":{"property":[
                {"name":"user_defined_keyspace","value":"%s"},
                {"name":"user_defined_namespace","value":"%s"}
              ]}
            }
            """, request.getUser_name(), request.getUser_name());
        HttpEntity<String> entity = new HttpEntity<>(payload, buildAuthHeaders(token));
        try {
            String body = restTemplate.postForEntity(url, entity, String.class).getBody();
            JsonNode node = objectMapper.readTree(body);
            String buildId = node.path("id").asText();
            if (buildId.isEmpty()) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No build id");
            cyodaEnvJobs.put(buildId, new JobStatus("processing", Instant.now()));
            return Map.of("build_id", buildId);
        } catch (Exception e) {
            logger.error("Error deployCyodaEnv", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping(path = "/user-app", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> deployUserApp(@RequestHeader("Authorization") String authorization,
                                             @RequestBody @Valid DeployUserAppRequest request) {
        logger.info("deployUserApp for user {} repo {}", request.getUser_name(), request.getRepository_url());
        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/buildQueue";
        String payload = String.format("""
            {
              "buildType":{"id":"KubernetesPipeline_CyodaSaasUserEnv"},
              "properties":{"property":[
                {"name":"repository_url","value":"%s"},
                {"name":"user_defined_namespace","value":"%s"}
              ]}
            }
            """, request.getRepository_url(), request.getUser_name());
        HttpEntity<String> entity = new HttpEntity<>(payload, buildAuthHeaders(token));
        try {
            String body = restTemplate.postForEntity(url, entity, String.class).getBody();
            JsonNode node = objectMapper.readTree(body);
            String buildId = node.path("id").asText();
            if (buildId.isEmpty()) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No build id");
            userAppJobs.put(buildId, new JobStatus("processing", Instant.now()));
            return Map.of("build_id", buildId);
        } catch (Exception e) {
            logger.error("Error deployUserApp", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping(path = "/cyoda-env/status/{build_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getCyodaEnvStatus(@RequestHeader("Authorization") String authorization,
                                      @PathVariable("build_id") @NotBlank String buildId) {
        logger.info("getCyodaEnvStatus for {}", buildId);
        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/buildQueue/id:" + buildId;
        return fetchJson(url, token);
    }

    @GetMapping(path = "/cyoda-env/statistics/{build_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getCyodaEnvStatistics(@RequestHeader("Authorization") String authorization,
                                          @PathVariable("build_id") @NotBlank String buildId) {
        logger.info("getCyodaEnvStatistics for {}", buildId);
        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/builds/id:" + buildId + "/statistics/";
        return fetchJson(url, token);
    }

    @GetMapping(path = "/user-app/status/{build_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getUserAppStatus(@RequestHeader("Authorization") String authorization,
                                     @PathVariable("build_id") @NotBlank String buildId) {
        logger.info("getUserAppStatus for {}", buildId);
        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/buildQueue/id:" + buildId;
        return fetchJson(url, token);
    }

    @GetMapping(path = "/user-app/statistics/{build_id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode getUserAppStatistics(@RequestHeader("Authorization") String authorization,
                                         @PathVariable("build_id") @NotBlank String buildId) {
        logger.info("getUserAppStatistics for {}", buildId);
        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/builds/id:" + buildId + "/statistics/";
        return fetchJson(url, token);
    }

    @PostMapping(path = "/cancel/user-app/{build_id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> cancelUserAppBuild(@RequestHeader("Authorization") String authorization,
                                                  @PathVariable("build_id") @NotBlank String buildId,
                                                  @RequestBody @Valid CancelBuildRequest request) {
        logger.info("cancelUserAppBuild for {} comment {}", buildId, request.getComment());
        String token = extractToken(authorization);
        String url = TEAMCITY_BASE_URL + "/builds/id:" + buildId;
        String payload = String.format("""
            {"comment":"%s","readdIntoQueue":%s}
            """, request.getComment(), request.isReaddIntoQueue());
        HttpEntity<String> entity = new HttpEntity<>(payload, buildAuthHeaders(token));
        try {
            restTemplate.postForEntity(url, entity, String.class);
            userAppJobs.put(buildId, new JobStatus("canceled", Instant.now()));
            return Map.of("result", "success");
        } catch (Exception e) {
            logger.error("Error cancelUserAppBuild", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private JsonNode fetchJson(String url, String token) {
        HttpEntity<Void> entity = new HttpEntity<>(buildAuthHeaders(token));
        try {
            String body = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, String.class).getBody();
            return objectMapper.readTree(body);
        } catch (Exception e) {
            logger.error("Error fetching JSON from {}", url, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getMessage());
        return Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason() != null ? ex.getReason() : ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleException(Exception ex) {
        logger.error("Unhandled exception", ex);
        return Map.of("error", HttpStatus.INTERNAL_SERVER_ERROR.toString(), "message", "Unexpected error");
    }
}